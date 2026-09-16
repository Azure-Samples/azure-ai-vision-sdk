/**
 * Per-phase helpers for verifyiOSAuth.
 *
 *   parseIosAttestEnvelope          — JSON + CBOR decode, fmt check
 *   verifyIosX5cChain               — credCert → intermediate → Apple root
 *   validateIosChainValidityDates   — notBefore/notAfter (always hard-fail)
 *   parseAttestAuthData             — fixed-layout decode
 *   verifyAppIdRpIdHash             — rpIdHash == SHA-256(IOS_APP_ID)
 *   verifyAaguidPolicy              — prod vs. dev gating
 *   verifyCredentialIdBindsPubKey   — credentialId == SHA-256(EC point)
 *   verifyAttestationNonceBinding   — credCert nonce == SHA-256(authData || challengeBytes)
 *   verifyAssertionAgainstAuthCert  — assertion proves auth-cert ownership
 *
 * Each returns either its result payload or an `IosPhaseFail` carrying the
 * reason code + extras for the main verifier's `failVerify`.
 */
import crypto from 'crypto';
import type { AttestationMessageData } from '../auth_verification';
import { pemToDer, validateCertificatePath, matchesPinnedCA } from '../cert_utils';
import { trackEvent, trackException } from '../logging';
import { getAttestationConfig } from '../config';
import {
  APPLE_APP_ATTEST_ROOT_CAS,
  AAGUID_PROD,
  AAGUID_DEV,
  AAGUID_OFFSET,
  AUTH_DATA_HEADER_BYTES,
  CREDENTIAL_ID_LENGTH_OFFSET,
  CREDENTIAL_ID_OFFSET,
} from './constants';
import {
  parseAppAttestToken,
  parseAppAttestAssertion,
  parseAssertionAuthData,
  extractNonceFromCredCert,
} from './parsers';
import {
  IosPhaseFail,
  type IosBaseProps,
  type IosAttestJson,
  type AppAttestObject,
  type AppAttestAssertionObject,
} from './types';

/**
 * Phase 1: parse the outer JSON wrapper, decode the CBOR attestation and
 * assertion blobs, and validate fmt === 'apple-appattest'. Returns the two
 * decoded objects.
 */
export function parseIosAttestEnvelope(
  attestJson: string,
  baseProps: IosBaseProps,
): { parsedToken: AppAttestObject; parsedAssertion: AppAttestAssertionObject } | IosPhaseFail {
  let parsed: IosAttestJson;
  try {
    parsed = JSON.parse(attestJson);
  } catch (e) {
    trackException(e, { source: 'verifyiOSAuth.parseAttestJson', ...baseProps });
    return new IosPhaseFail(
      'ATTEST_JSON_PARSE_ERROR',
      'Invalid attestation: failed to parse attestJson',
    );
  }
  if (!parsed.attestation || typeof parsed.attestation !== 'string') {
    return new IosPhaseFail(
      'MISSING_ATTESTATION',
      'Invalid attestation: missing attestation field',
    );
  }
  if (!parsed.assertion || typeof parsed.assertion !== 'string') {
    return new IosPhaseFail(
      'MISSING_ASSERTION',
      'Invalid attestation: missing assertion (auth-cert binding proof)',
    );
  }

  let parsedToken: AppAttestObject;
  try {
    parsedToken = parseAppAttestToken(parsed.attestation);
  } catch (e) {
    trackException(e, { source: 'verifyiOSAuth.parseAttestation', ...baseProps });
    return new IosPhaseFail(
      'ATTESTATION_DECODE_ERROR',
      `Invalid attestation: ${e instanceof Error ? e.message : 'failed to decode App Attest attestation'}`,
    );
  }

  if (parsedToken.fmt !== 'apple-appattest') {
    return new IosPhaseFail('UNEXPECTED_FMT', `Unexpected fmt: ${parsedToken.fmt}`, undefined, {
      fmt: parsedToken.fmt,
    });
  }

  let parsedAssertion: AppAttestAssertionObject;
  try {
    parsedAssertion = parseAppAttestAssertion(parsed.assertion);
  } catch (e) {
    trackException(e, { source: 'verifyiOSAuth.parseAssertion', ...baseProps });
    return new IosPhaseFail(
      'ASSERTION_DECODE_ERROR',
      `Invalid assertion: ${e instanceof Error ? e.message : 'failed to decode App Attest assertion'}`,
    );
  }

  trackEvent('IosAuth.AttestationParsed', {
    fmt: parsedToken.fmt,
    attestAuthDataLength: parsedToken.authData.length,
    credCertDerLength: parsedToken.credCertDer.length,
    intermediateDerLength: parsedToken.intermediateDer.length,
    receiptLength: parsedToken.receiptLength,
    assertAuthDataLength: parsedAssertion.authenticatorData.length,
    signatureLength: parsedAssertion.signature.length,
  });

  return { parsedToken, parsedAssertion };
}

/**
 * Phase 2: verify the x5c chain back to the Apple App Attestation Root CA.
 * Accepts both 2-cert chains (credCert + intermediate, intermediate is the
 * pinned root) and 3-cert chains (credCert + intermediate + root, where the
 * intermediate must be signed by the pinned root).
 */
export async function verifyIosX5cChain(
  credCertDer: Buffer,
  intermediateDer: Buffer,
): Promise<null | IosPhaseFail> {
  const chain = [credCertDer, intermediateDer];
  if (!matchesPinnedCA(intermediateDer, APPLE_APP_ATTEST_ROOT_CAS)) {
    chain.push(pemToDer(APPLE_APP_ATTEST_ROOT_CAS[0]));
  }
  if (!await validateCertificatePath(chain, APPLE_APP_ATTEST_ROOT_CAS)) {
    return new IosPhaseFail('CHAIN_PATH_INVALID', 'Certificate path validation failed against the Apple App Attestation Root CA');
  }

  try {
    const credCert = new crypto.X509Certificate(credCertDer);
    const intCert = new crypto.X509Certificate(intermediateDer);
    trackEvent('IosAuth.CertificateChainVerified', {
      credCertSubject: credCert.subject,
      credCertIssuer: credCert.issuer,
      credCertValidFrom: credCert.validFrom,
      credCertValidTo: credCert.validTo,
      intermediateSubject: intCert.subject,
      intermediateIssuer: intCert.issuer,
    });
  } catch {
    // logging only — primary validation already passed
  }
  return null;
}

/**
 * Phase 3: cert validity windows. Out-of-validity is always hard-fail —
 * DEBUG_MODE does not relax cert validity (chain cert validity is a
 * cryptographic invariant we don't bend, even for develop builds).
 */
export function validateIosChainValidityDates(
  credCertDer: Buffer,
  intermediateDer: Buffer,
  baseProps: IosBaseProps,
): null | IosPhaseFail {
  const now = new Date();
  try {
    for (const [label, der] of [
      ['credCert', credCertDer] as const,
      ['intermediate', intermediateDer] as const,
    ]) {
      const c = new crypto.X509Certificate(der);
      const vf = new Date(c.validFrom);
      const vt = new Date(c.validTo);
      if (now < vf || now > vt) {
        return new IosPhaseFail(
          'CHAIN_CERT_NOT_VALID',
          `${label} is not valid (valid from ${c.validFrom} to ${c.validTo})`,
        );
      }
    }
  } catch (e) {
    trackException(e, { source: 'verifyiOSAuth.validityDates', ...baseProps });
    return new IosPhaseFail(
      'CERT_VALIDITY_ERROR',
      'Failed to validate certificate validity dates',
    );
  }
  return null;
}

/**
 * Phase 4: parse the fixed-layout fields out of attestation authData:
 *   rpIdHash (32) || flags (1) || signCount (4) || aaguid (16)
 *     || credIdLen (2) || credentialId (credIdLen)
 */
export function parseAttestAuthData(authData: Buffer):
  | {
      rpIdHash: Buffer;
      flags: number;
      signCount: number;
      aaguid: Buffer;
      credIdLen: number;
      credentialId: Buffer;
    }
  | IosPhaseFail {
  if (authData.length < AUTH_DATA_HEADER_BYTES) {
    return new IosPhaseFail('AUTHDATA_TOO_SHORT', `authData is ${authData.length} bytes, < ${AUTH_DATA_HEADER_BYTES}`);
  }
  const { rpIdHash, flags, signCount } = parseAssertionAuthData(authData);
  if (authData.length < CREDENTIAL_ID_OFFSET) {
    return new IosPhaseFail(
      'AUTHDATA_MISSING_ATTESTED',
      'authData missing attested credential data',
    );
  }
  const aaguid = authData.subarray(AAGUID_OFFSET, CREDENTIAL_ID_LENGTH_OFFSET);
  const credIdLen = authData.readUInt16BE(CREDENTIAL_ID_LENGTH_OFFSET);
  if (authData.length < CREDENTIAL_ID_OFFSET + credIdLen) {
    return new IosPhaseFail('AUTHDATA_TRUNCATED', 'authData truncated within credentialId');
  }
  const credentialId = authData.subarray(CREDENTIAL_ID_OFFSET, CREDENTIAL_ID_OFFSET + credIdLen);
  return { rpIdHash, flags, signCount, aaguid, credIdLen, credentialId };
}

/** Phase 5: authData.rpIdHash must equal SHA-256(IOS_APP_ID). */
export function verifyAppIdRpIdHash(rpIdHash: Buffer):
  | { expectedAppId: string; expectedRpIdHash: Buffer }
  | IosPhaseFail {
  const expectedAppId = getAttestationConfig().iosAppId;
  if (!expectedAppId) {
    return new IosPhaseFail(
      'MISSING_APP_ID_ENV',
      'IOS_APP_ID environment variable not set (expected "<TeamID>.<BundleID>")',
    );
  }
  const expectedRpIdHash = crypto.createHash('sha256').update(expectedAppId, 'utf8').digest();
  if (!expectedRpIdHash.equals(rpIdHash)) {
    return new IosPhaseFail(
      'APP_ID_MISMATCH',
      'authData.rpIdHash does not match SHA-256(IOS_APP_ID)',
      undefined,
      {
        rpIdHashPrefix: rpIdHash.toString('hex').slice(0, 12),
        expectedAppId,
      },
    );
  }
  return { expectedAppId, expectedRpIdHash };
}

/**
 * Phase 6: gate on aaguid. Production ("appattest" + NULs) always passes; the
 * development aaguid ("appattestdevelop") only passes when DEBUG_MODE=true,
 * and surfaces as a warning.
 */
export function verifyAaguidPolicy(
  aaguid: Buffer,
  debugMode: boolean,
  warningsSink: string[],
): null | IosPhaseFail {
  const isProdAaguid = aaguid.equals(AAGUID_PROD);
  const isDevAaguid = aaguid.equals(AAGUID_DEV);
  if (!isProdAaguid && !isDevAaguid) {
    return new IosPhaseFail(
      'UNKNOWN_AAGUID',
      `Unknown aaguid in authData: ${aaguid.toString('hex')}`,
      undefined,
      { aaguidHex: aaguid.toString('hex') },
    );
  }
  if (isDevAaguid && !debugMode) {
    return new IosPhaseFail(
      'DEV_AAGUID_NOT_ALLOWED',
      'authData.aaguid is "appattestdevelop" but DEBUG_MODE is not enabled',
      undefined,
      { aaguidUtf8: aaguid.toString('utf8') },
    );
  }
  if (isDevAaguid) {
    warningsSink.push('aaguid is "appattestdevelop" (debug mode)');
  }
  return null;
}

/**
 * Phase 7: credentialId must equal SHA-256(uncompressed P-256 EC point) of
 * credCert's public key. This proves authData claims the same key as the
 * cert.
 */
export function verifyCredentialIdBindsPubKey(
  credCertDer: Buffer,
  credentialId: Buffer,
  baseProps: IosBaseProps,
): { credCertPubKey: crypto.KeyObject; credCertPubKeyPoint: Buffer } | IosPhaseFail {
  let credCertPubKey: crypto.KeyObject;
  let credCertPubKeyPoint: Buffer;
  try {
    const credCert = new crypto.X509Certificate(credCertDer);
    credCertPubKey = credCert.publicKey;
    const spki = credCertPubKey.export({ type: 'spki', format: 'der' }) as Buffer;
    credCertPubKeyPoint = spki.subarray(-65);
    if (credCertPubKeyPoint.length !== 65 || credCertPubKeyPoint[0] !== 0x04) {
      return new IosPhaseFail(
        'CRED_PUBKEY_FORMAT',
        'credCert public key is not uncompressed P-256',
      );
    }
  } catch (e) {
    trackException(e, { source: 'verifyiOSAuth.extractCredPubkey', ...baseProps });
    return new IosPhaseFail('CRED_PUBKEY_EXTRACT', 'Failed to extract credCert public key');
  }
  const expectedCredId = crypto.createHash('sha256').update(credCertPubKeyPoint).digest();
  if (!expectedCredId.equals(credentialId)) {
    return new IosPhaseFail(
      'CREDENTIAL_ID_MISMATCH',
      'credentialId in authData does not match SHA-256 of credCert public key',
    );
  }
  return { credCertPubKey, credCertPubKeyPoint };
}

/**
 * Phase 8: attestation nonce binding —
 *   expectedNonce = SHA-256( authData || hex2bytes(challengeHash) )
 *   expectedNonce == nonce OCTET STRING extracted from credCert
 *     extension OID 1.2.840.113635.100.8.2
 */
export function verifyAttestationNonceBinding(
  credCertDer: Buffer,
  authData: Buffer,
  messageData: AttestationMessageData,
): { certNonce: Buffer; challengeBytes: Buffer } | IosPhaseFail {
  if (!messageData.publicCert) {
    return new IosPhaseFail('MISSING_PUBLIC_CERT', 'Missing publicCert in message data');
  }
  if (!messageData.challengeHash) {
    return new IosPhaseFail(
      'MISSING_CHALLENGE_HASH',
      'Missing challengeHash in message data',
    );
  }
  if (!/^[0-9a-f]{64}$/i.test(messageData.challengeHash)) {
    return new IosPhaseFail(
      'CHALLENGE_HASH_FORMAT',
      'challengeHash must be a 64-char hex string',
    );
  }
  const challengeBytes = Buffer.from(messageData.challengeHash, 'hex'); // 32 bytes
  const expectedNonce = crypto
    .createHash('sha256')
    .update(authData)
    .update(challengeBytes)
    .digest();

  const certNonce = extractNonceFromCredCert(credCertDer);
  if (!certNonce) {
    return new IosPhaseFail(
      'CRED_NONCE_EXT_MISSING',
      'credCert is missing the App Attest nonce extension (OID 1.2.840.113635.100.8.2)',
    );
  }
  if (!expectedNonce.equals(certNonce)) {
    return new IosPhaseFail(
      'CHALLENGE_NONCE_MISMATCH',
      'App Attest challenge binding failed: cert nonce does not match SHA-256(authData || challengeHash bytes)',
      undefined,
      {
        expectedPrefix: expectedNonce.toString('hex').slice(0, 12),
        gotPrefix: certNonce.toString('hex').slice(0, 12),
      },
    );
  }

  trackEvent('IosAuth.AttestationNonceBindingVerified', {
    challengeHashPrefix: messageData.challengeHash.slice(0, 16),
    noncePrefix: expectedNonce.toString('hex').slice(0, 16),
  });
  return { certNonce, challengeBytes };
}

/**
 * Phase 9: prove the auth cert being registered belongs to the attested key.
 * The assertion is generated by DCAppAttestService.generateAssertion against
 * `clientData = authCertDER`; its signature is verified with credCert's
 * public key over `nonce = SHA-256(authenticatorData || SHA-256(authCertDER))`.
 *
 * Also enforces:
 *   - assertion.rpIdHash == attestation.rpIdHash (same app + key)
 *   - assertion.signCount > attestation.signCount (monotonic)
 *
 * Tries DER signature encoding first (Apple's documented format) and falls
 * back to IEEE P1363 (raw r||s) in case Apple changes encoding.
 */
export function verifyAssertionAgainstAuthCert(args: {
  parsedAssertion: AppAttestAssertionObject;
  credCertPubKey: crypto.KeyObject;
  attestRpIdHash: Buffer;
  attestSignCount: number;
  authCertPem: string;
  baseProps: IosBaseProps;
}):
  | {
      assertionAuthData: Buffer;
      assertionRpIdHash: Buffer;
      assertionFlags: number;
      assertionSignCount: number;
      signatureEncodingUsed: 'der' | 'ieee-p1363';
      authCertThumbprint: Buffer;
      authCertThumbprintHex: string;
    }
  | IosPhaseFail {
  const { parsedAssertion, credCertPubKey, attestRpIdHash, attestSignCount, authCertPem, baseProps } = args;

  const authCertDer = pemToDer(authCertPem);
  const authCertThumbprint = crypto.createHash('sha256').update(authCertDer).digest(); // 32 bytes
  const authCertThumbprintHex = authCertThumbprint.toString('hex');

  const assertionAuthData = parsedAssertion.authenticatorData;
  const {
    rpIdHash: assertionRpIdHash,
    flags: assertionFlags,
    signCount: assertionSignCount,
  } = parseAssertionAuthData(assertionAuthData);

  // rpIdHash on the assertion must match the attestation — same app, same key
  if (!assertionRpIdHash.equals(attestRpIdHash)) {
    return new IosPhaseFail(
      'ASSERTION_RPID_MISMATCH',
      'Assertion rpIdHash does not match attestation rpIdHash',
      undefined,
      {
        attestRpIdPrefix: attestRpIdHash.toString('hex').slice(0, 12),
        assertRpIdPrefix: assertionRpIdHash.toString('hex').slice(0, 12),
      },
    );
  }

  // Strictly-increasing signCount across attest→assert. Attestation always
  // reports 0; the first generateAssertion call must report ≥ 1.
  if (assertionSignCount <= attestSignCount) {
    return new IosPhaseFail(
      'ASSERTION_SIGNCOUNT_NOT_INCREMENTED',
      `Assertion signCount (${assertionSignCount}) is not greater than attestation signCount (${attestSignCount})`,
      undefined,
      { attestSignCount, assertSignCount: assertionSignCount },
    );
  }

  // Per Apple's spec
  // (https://developer.apple.com/documentation/devicecheck/validating-apps-that-connect-to-your-server):
  //   1. clientDataHash = SHA-256(clientData)            // = authCertThumbprint
  //   2. nonce          = SHA-256(authData || clientDataHash)
  //   3. signature      = ECDSA_with_SHA256(privKey, nonce)
  // "Signature is valid for nonce" means nonce is the *message* signed by
  // ECDSA-with-SHA256 — ECDSA still hashes it once more internally, so the
  // verify input is `nonce`, NOT `authData || clientDataHash`. Passing the
  // concatenation directly would only do two SHA-256s (inside verify) and
  // miss the third one ECDSA applies to nonce.
  const assertionNonce = crypto
    .createHash('sha256')
    .update(assertionAuthData)
    .update(authCertThumbprint)
    .digest();
  let assertionSignatureOk = false;
  let signatureEncodingUsed: 'der' | 'ieee-p1363' = 'der';
  try {
    assertionSignatureOk = crypto.verify(
      'sha256',
      assertionNonce,
      credCertPubKey,
      parsedAssertion.signature,
    );
    if (!assertionSignatureOk) {
      signatureEncodingUsed = 'ieee-p1363';
      assertionSignatureOk = crypto.verify(
        'sha256',
        assertionNonce,
        { key: credCertPubKey, dsaEncoding: 'ieee-p1363' },
        parsedAssertion.signature,
      );
    }
  } catch (e) {
    trackException(e, { source: 'verifyiOSAuth.assertionVerify', ...baseProps });
  }
  if (!assertionSignatureOk) {
    return new IosPhaseFail(
      'ASSERTION_SIGNATURE_INVALID',
      'Assertion signature does not verify against credCert public key for nonce = SHA-256(authenticatorData || sha256(authCertDER))',
      undefined,
      {
        authCertThumbprintPrefix: authCertThumbprintHex.slice(0, 16),
        signatureLength: parsedAssertion.signature.length,
        assertSignCount: assertionSignCount,
      },
    );
  }

  trackEvent('IosAuth.AssertionVerified', {
    assertSignCount: assertionSignCount,
    signatureEncoding: signatureEncodingUsed,
    authCertThumbprintPrefix: authCertThumbprintHex.slice(0, 16),
  });

  return {
    assertionAuthData,
    assertionRpIdHash,
    assertionFlags,
    assertionSignCount,
    signatureEncodingUsed,
    authCertThumbprint,
    authCertThumbprintHex,
  };
}
