/**
 * Per-phase certificate-chain validators for Android Key Attestation.
 *
 *   parseAndroidAttestJson              — JSON.parse + structural checks
 *   buildAndroidChainDerBuffers         — base64 chain + PEM leaf → DER buffers
 *   verifyLeafBoundToChain0             — auth cert must be the attested chain leaf
 *   validateAndroidChainValidityDates   — notBefore/notAfter sweep
 *   verifyAndroidChainSignaturesAndRoot — cert[i] signed by cert[i+1] + pinned root
 *   verifyAttestationSecurityLevel      — require TEE or StrongBox attestation
 *   verifyAndroidChainRevocations       — hits the Google revocation list
 *   verifyChainBindsSessionChallenge    — keymaster ext == sessionChallengeHash
 *
 * Each helper returns either its result payload or an `AndroidPhaseFail` that
 * the main verifier turns into the matching `failVerify` call.
 */
import crypto from 'crypto';
import { pemToDer, validateCertificatePath, matchesPinnedCA } from '../cert_utils';
import { GOOGLE_HARDWARE_ATTESTATION_ROOT_CAS } from './google_roots';
import { trackEvent, trackException } from '../logging';
import { androidAttestationSchema, AndroidPhaseFail, type AndroidAttestationJson, type BaseProps } from './types';
import { fetchRevocationStatusList, checkCertificateRevocation } from './revocation';
import {
  extractAttestationChallengeFromCert,
  isHardwareAttestationSecurityLevel,
  parseKeyDescription,
} from './keymaster_ext';

/**
 * Phase 1: parse the JSON envelope and validate the certificateChain field
 * exists, is an array, and is non-empty.
 */
export function parseAndroidAttestJson(
  attestJson: string,
  baseProps: BaseProps,
): AndroidAttestationJson | AndroidPhaseFail {
  let parsed: unknown;
  try {
    parsed = JSON.parse(attestJson);
  } catch (parseErr) {
    trackException(parseErr, { source: 'verifyAndroidAuth.parseAttestJson', ...baseProps });
    return new AndroidPhaseFail(
      'ATTEST_JSON_PARSE_ERROR',
      'Invalid attestation: failed to parse attestJson',
    );
  }

  const result = androidAttestationSchema.safeParse(parsed);
  if (!result.success) {
    const issue = result.error.issues[0];
    if (issue.path[0] === 'token') {
      return new AndroidPhaseFail('MISSING_INTEGRITY_TOKEN', 'Play Integrity token must be a string');
    }
    if (issue.path[0] === 'certificateChain' && issue.code === 'too_small') {
      return new AndroidPhaseFail('EMPTY_CHAIN', 'Invalid attestation: empty certificateChain');
    }
    return new AndroidPhaseFail(
      'INVALID_CHAIN_FORMAT',
      'Invalid attestation: missing or invalid certificateChain',
    );
  }

  trackEvent('AndroidAuth.AttestationParsed', {
    hasIntegrityToken: !!result.data.token,
    integrityTokenLength: result.data.token?.length ?? 0,
    hasCertificateChain: true,
    certificateChainLength: result.data.certificateChain.length,
  });
  return result.data;
}

/**
 * Phase 2: turn the base64 chain + PEM leaf into DER buffers.
 */
export function buildAndroidChainDerBuffers(
  attestData: AndroidAttestationJson,
  publicCertPem: string | undefined,
): { certChainDer: Buffer[]; leafCertDer: Buffer } | AndroidPhaseFail {
  if (!publicCertPem) {
    return new AndroidPhaseFail('MISSING_PUBLIC_CERT', 'Missing publicCert in message data');
  }

  const certChainDer: Buffer[] = attestData.certificateChain.map((base64Cert) =>
    Buffer.from(base64Cert, 'base64'),
  );
  const leafCertDer = pemToDer(publicCertPem);

  trackEvent('AndroidAuth.CertificateChainDecoded', {
    certificateChainLength: certChainDer.length,
    leafCertificateLength: leafCertDer.length,
    chainCertificateLengths: certChainDer.map(cert => cert.length),
  });
  return { certChainDer, leafCertDer };
}

/**
 * Phase 3: the auth cert (messageData.publicCert) must be byte-identical to
 * certificateChain[0], the hardware-attested leaf. Accepting a separate cert
 * merely signed by chain[0] would allow the hardware key to delegate trust to
 * an exportable software key.
 */
export function verifyLeafBoundToChain0(
  leafCertDer: Buffer,
  certChainDer: Buffer[],
): null | AndroidPhaseFail {
  const leafMatchesChain0 = leafCertDer.equals(certChainDer[0]);
  if (leafMatchesChain0) return null;

  try {
    const leafCert = new crypto.X509Certificate(leafCertDer);
    const chain0Cert = new crypto.X509Certificate(certChainDer[0]);
    trackEvent('AndroidAuth.LeafChainMismatch', {
      leafSubject: leafCert.subject,
      leafIssuer: leafCert.issuer,
      leafFingerprintPrefix: leafCert.fingerprint256.slice(0, 23),
      chain0Subject: chain0Cert.subject,
      chain0Issuer: chain0Cert.issuer,
      chain0FingerprintPrefix: chain0Cert.fingerprint256.slice(0, 23),
      certificateChainLength: certChainDer.length,
    });
  } catch (error) {
    trackException(error, { source: 'verifyAndroidAuth.compareLeafAndChain' });
  }
  return new AndroidPhaseFail(
    'LEAF_CHAIN_MISMATCH',
    'Certificate chain verification failed: publicCert must match certificateChain[0]',
    { chainLength: certChainDer.length },
    { chainLength: certChainDer.length },
  );
}

/**
 * Phase 4: validity-date sweep across leaf + chain. Leaf-out-of-validity is
 * logged as a warning and surfaced to the caller; any chain cert out of
 * validity hard-fails, even under DEBUG_MODE.
 */
export function validateAndroidChainValidityDates(
  leafCertDer: Buffer,
  certChainDer: Buffer[],
  baseProps: BaseProps,
): { leafCertValidityWarning?: string } | AndroidPhaseFail {
  try {
    const leafCert = new crypto.X509Certificate(leafCertDer);
    const now = new Date();
    const validFrom = new Date(leafCert.validFrom);
    const validTo = new Date(leafCert.validTo);

    let leafCertValidityWarning: string | undefined;
    if (now < validFrom || now > validTo) {
      leafCertValidityWarning = `Leaf certificate validity warning: valid from ${leafCert.validFrom} to ${leafCert.validTo}`;
      trackEvent('AndroidAuth.LeafCertValidityWarning', {
        ...baseProps,
        validFrom: leafCert.validFrom,
        validTo: leafCert.validTo,
      });
    }

    for (let i = 0; i < certChainDer.length; i++) {
      const cert = new crypto.X509Certificate(certChainDer[i]);
      const certValidFrom = new Date(cert.validFrom);
      const certValidTo = new Date(cert.validTo);

      if (now < certValidFrom || now > certValidTo) {
        return new AndroidPhaseFail(
          'CHAIN_CERT_NOT_VALID',
          `Certificate at index ${i} is not valid (valid from ${cert.validFrom} to ${cert.validTo})`,
          { chainLength: certChainDer.length + 1 },
          {
            chainIndex: i,
            certValidFrom: cert.validFrom,
            certValidTo: cert.validTo,
          },
        );
      }
    }

    return { leafCertValidityWarning };
  } catch (error) {
    trackException(error, { source: 'verifyAndroidAuth.validityDates', ...baseProps });
    return new AndroidPhaseFail(
      'CERT_VALIDITY_ERROR',
      'Failed to validate certificate validity dates',
    );
  }
}

/**
 * Phase 5: cert[i] must be signed by cert[i+1] for the entire chain, and the
 * root (last cert) must match one of the pinned Google Hardware Attestation
 * CAs.
 *
 * Returns the root CA subject string (best-effort, "unknown" if it doesn't
 * parse — purely for logging).
 */
export async function verifyAndroidChainSignaturesAndRoot(
  certChainDer: Buffer[],
): Promise<{ rootCASubject: string } | AndroidPhaseFail> {
  // pinned root CA
  const rootCert = certChainDer[certChainDer.length - 1];
  if (!matchesPinnedCA(rootCert, GOOGLE_HARDWARE_ATTESTATION_ROOT_CAS)) {
    try {
      const parsedRoot = new crypto.X509Certificate(rootCert);
      trackEvent('AndroidAuth.RootCAMismatch', {
        rootSubject: parsedRoot.subject,
        rootIssuer: parsedRoot.issuer,
        rootFingerprintPrefix: parsedRoot.fingerprint256.slice(0, 23),
        certificateChainLength: certChainDer.length,
        pinnedRootCount: GOOGLE_HARDWARE_ATTESTATION_ROOT_CAS.length,
      });
    } catch (error) {
      trackException(error, { source: 'verifyAndroidAuth.parseUntrustedRoot' });
    }
    return new AndroidPhaseFail(
      'ROOT_CA_MISMATCH',
      'Certificate chain verification failed: root CA does not match any pinned CA',
      { chainLength: certChainDer.length },
      { chainLength: certChainDer.length },
    );
  }

  if (!await validateCertificatePath(certChainDer, GOOGLE_HARDWARE_ATTESTATION_ROOT_CAS)) {
    return new AndroidPhaseFail('CHAIN_PATH_INVALID', 'Certificate path validation failed',
      { chainLength: certChainDer.length });
  }
  let rootCASubject = 'unknown';
  try {
    rootCASubject = new crypto.X509Certificate(rootCert).subject;
  } catch (error) {
    trackException(error, { source: 'verifyAndroidAuth.rootSubject' });
  }
  return { rootCASubject };
}

/**
 * Phase 6: require hardware-backed attestation from TEE or StrongBox.
 */
export function verifyAttestationSecurityLevel(
  chain0Der: Buffer,
): null | AndroidPhaseFail {
  const description = parseKeyDescription(chain0Der);
  if (!description) {
    return new AndroidPhaseFail(
      'KEYMASTER_EXT_MISSING',
      'Android Key Attestation extension (OID 1.3.6.1.4.1.11129.2.1.17) missing or unparseable on leaf attestation cert',
    );
  }
  if (!isHardwareAttestationSecurityLevel(description)) {
    return new AndroidPhaseFail(
      'ATTESTATION_SECURITY_LEVEL_INVALID',
      'Android Key Attestation verification failed: attestationSecurityLevel must be TrustedEnvironment or StrongBox',
      undefined,
      { attestationSecurityLevel: description.attestationSecurityLevel },
    );
  }
  return null;
}

/**
 * Phase 7: hit the Google revocation list and reject if either the leaf or
 * any chain cert is revoked/suspended. fetchRevocationStatusList fails closed
 * inside checkCertificateRevocation, so an unreachable list is also rejected.
 */
export async function verifyAndroidChainRevocations(
  leafCertDer: Buffer,
  certChainDer: Buffer[],
): Promise<null | AndroidPhaseFail> {
  const revocationStatusList = await fetchRevocationStatusList();

  const leafRevocation = checkCertificateRevocation(leafCertDer, revocationStatusList);
  if (leafRevocation.isRevoked) {
    return new AndroidPhaseFail(
      'LEAF_REVOKED',
      `Leaf certificate has been ${leafRevocation.status}: ${leafRevocation.reason}`,
      { chainLength: certChainDer.length + 1 },
      {
        revocationStatus: leafRevocation.status,
        revocationReason: leafRevocation.reason,
      },
    );
  }

  for (let i = 0; i < certChainDer.length; i++) {
    const revocationCheck = checkCertificateRevocation(certChainDer[i], revocationStatusList);
    if (revocationCheck.isRevoked) {
      return new AndroidPhaseFail(
        'CHAIN_CERT_REVOKED',
        `Certificate at index ${i} has been ${revocationCheck.status}: ${revocationCheck.reason}`,
        { chainLength: certChainDer.length + 1 },
        {
          chainIndex: i,
          revocationStatus: revocationCheck.status,
          revocationReason: revocationCheck.reason,
        },
      );
    }
  }
  return null;
}

/**
 * Phase 8: the keymaster attestation extension on the leaf must carry the
 * *current* session challengeHash. This is the primary session-binding proof
 * for Android — pre-rotation chains from earlier sessions are explicitly
 * rejected here, even when their certificate chain still validates
 * cryptographically.
 */
export function verifyChainBindsSessionChallenge(
  chain0Der: Buffer,
  sessionChallengeHash: string | undefined,
): { chainChallengeHex: string } | AndroidPhaseFail {
  if (!sessionChallengeHash) {
    return new AndroidPhaseFail(
      'MISSING_CHALLENGE_HASH',
      'Missing challengeHash in message data',
    );
  }
  if (!/^[0-9a-f]+$/i.test(sessionChallengeHash) || sessionChallengeHash.length % 2 !== 0) {
    return new AndroidPhaseFail(
      'CHALLENGE_HASH_FORMAT',
      'challengeHash must be a hex string of even length',
    );
  }
  const sessionChallengeBytes = Buffer.from(sessionChallengeHash, 'hex');
  const chainChallenge = extractAttestationChallengeFromCert(chain0Der);
  if (!chainChallenge) {
    return new AndroidPhaseFail(
      'KEYMASTER_EXT_MISSING',
      'Android Key Attestation extension (OID 1.3.6.1.4.1.11129.2.1.17) missing or unparseable on leaf attestation cert',
    );
  }
  if (!chainChallenge.equals(sessionChallengeBytes)) {
    return new AndroidPhaseFail(
      'CHAIN_CHALLENGE_MISMATCH',
      'Android Key Attestation chain challenge does not match session challengeHash',
      undefined,
      {
        expectedPrefix: sessionChallengeBytes.toString('hex').slice(0, 12),
        gotPrefix: chainChallenge.toString('hex').slice(0, 12),
      },
    );
  }
  return { chainChallengeHex: chainChallenge.toString('hex') };
}
