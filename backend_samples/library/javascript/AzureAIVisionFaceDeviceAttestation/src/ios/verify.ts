/**
 * Top-level iOS App Attest orchestrator.
 *
 * Each verification step is one named phase in `attestation_phases.ts`; this
 * file is just the sequencer + telemetry adapter. Phases return their result
 * payload on success, or an `IosPhaseFail` carrying the reason code + extras
 * — `failVerify` turns that into the AuthVerificationResult the caller
 * (auth_verification.verifyAuthBySystem) already expects.
 *
 *   attestation:
 *     {
 *       "fmt":     "apple-appattest",
 *       "attStmt": { "x5c": [credCert, intermediateCert], "receipt": <bytes> },
 *       "authData": <bytes>,
 *     }
 *
 *   assertion:
 *     {
 *       "signature":         <ECDSA-P256-SHA256 DER bytes>,
 *       "authenticatorData": <37+ bytes: rpIdHash || flags || signCount || ...>
 *     }
 *
 *   attestation clientDataHash  = Buffer.from(messageData.challengeHash, 'hex')
 *   expectedAttestNonce         = SHA-256( authData || attestation clientDataHash )
 *   expectedAttestNonce        == nonce OCTET STRING in credCert ext 1.2.840.113635.100.8.2
 *
 *   assertion clientDataHash    = Buffer.from(sha256(authPublicCertDER).hex, 'hex')
 *   nonce                       = SHA-256( assertion.authenticatorData || assertion clientDataHash )
 *   ECDSA_with_SHA256_verify( credCertPubKey, signature, nonce )  ==  true
 *
 * Why two proofs instead of one folded hash:
 *   - Attestation proves Apple-hardware provenance against the server challenge.
 *   - Assertion proves the auth cert that's being registered belongs to the
 *     same attested key. Splitting the two lets us audit and reason about each
 *     independently, and matches Apple's intended use of the two APIs.
 */
import crypto from 'crypto';
import type { AuthVerificationResult, AttestationMessageData } from '../auth_verification';
import { trackEvent, trackException } from '../logging';
import { getAttestationConfig } from '../config';
import { IosPhaseFail, type IosBaseProps, type AppAttestVerdict } from './types';
import {
  parseIosAttestEnvelope,
  verifyIosX5cChain,
  validateIosChainValidityDates,
  parseAttestAuthData,
  verifyAppIdRpIdHash,
  verifyAaguidPolicy,
  verifyCredentialIdBindsPubKey,
  verifyAttestationNonceBinding,
  verifyAssertionAgainstAuthCert,
} from './attestation_phases';

/**
 * Verify Apple App Attest attestation + assertion.
 *
 * @param messageData - Parsed message object (challengeHash, clientId, system, publicCert)
 * @param attestJson  - JSON string `{ "attestation": "<base64 CBOR>", "assertion": "<base64 CBOR>" }`
 * @returns Verification result.
 */
export async function verifyiOSAuth(
  messageData: AttestationMessageData,
  attestJson: string,
): Promise<AuthVerificationResult> {
  const startTime = Date.now();
  const baseProps: IosBaseProps = {
    platform: 'ios',
    clientId: messageData.clientId,
    challengeHashPrefix: messageData.challengeHash?.slice(0, 8),
    attestJsonLength: attestJson.length,
  };

  trackEvent('IosAuth.VerifyStart', baseProps);

  const failVerify = (
    reason: string,
    message: string,
    extras: Partial<AuthVerificationResult> = {},
    extraProps: Record<string, unknown> = {},
  ): AuthVerificationResult => {
    const durationMs = Date.now() - startTime;
    trackEvent(
      'IosAuth.VerifyFail',
      { ...baseProps, reason, message, ...extraProps },
      { durationMs },
    );
    return {
      verified: false,
      platform: 'ios',
      message,
      timestamp: new Date().toISOString(),
      ...extras,
    };
  };

  const fail = (f: IosPhaseFail) => failVerify(f.reason, f.message, f.extras, f.extraProps);

  try {
    const debugMode = getAttestationConfig().debugMode;

    // 1. parse JSON + CBOR envelopes
    const env = parseIosAttestEnvelope(attestJson, baseProps);
    if (env instanceof IosPhaseFail) return fail(env);
    const { parsedToken, parsedAssertion } = env;
    const { fmt, authData, credCertDer, intermediateDer, receiptLength, receipt } = parsedToken;

    const validityFail = validateIosChainValidityDates(credCertDer, intermediateDer, baseProps);
    if (validityFail) return fail(validityFail);
    const chainFail = await verifyIosX5cChain(credCertDer, intermediateDer);
    if (chainFail) return fail(chainFail);
    const warnings: string[] = [];

    // 4. authData layout
    const authDataRes = parseAttestAuthData(authData);
    if (authDataRes instanceof IosPhaseFail) return fail(authDataRes);
    const { rpIdHash, flags, signCount, aaguid, credIdLen, credentialId } = authDataRes;

    // 5. app id (rpIdHash binding)
    const appIdRes = verifyAppIdRpIdHash(rpIdHash);
    if (appIdRes instanceof IosPhaseFail) return fail(appIdRes);
    const { expectedAppId } = appIdRes;

    // 6. aaguid policy (prod/dev gating)
    const aaguidFail = verifyAaguidPolicy(aaguid, debugMode, warnings);
    if (aaguidFail) return fail(aaguidFail);

    trackEvent('IosAuth.AuthDataParsed', {
      rpIdHashPrefix: rpIdHash.toString('hex').slice(0, 16),
      flags,
      signCount,
      aaguid: aaguid.toString('utf8').replace(/\0+$/, ''),
      credentialIdLength: credIdLen,
      credentialIdPrefix: credentialId.toString('hex').slice(0, 16),
      expectedAppId,
      appIdMatched: true,
    });

    // 7. credentialId == SHA-256(uncompressed EC point)
    const credBindRes = verifyCredentialIdBindsPubKey(credCertDer, credentialId, baseProps);
    if (credBindRes instanceof IosPhaseFail) return fail(credBindRes);
    const { credCertPubKey } = credBindRes;

    // 8. attestation nonce binding to session challenge
    const nonceRes = verifyAttestationNonceBinding(credCertDer, authData, messageData);
    if (nonceRes instanceof IosPhaseFail) return fail(nonceRes);
    const { certNonce, challengeBytes } = nonceRes;

    // 9. assertion proves the auth cert belongs to the attested key
    const assertionRes = verifyAssertionAgainstAuthCert({
      parsedAssertion,
      credCertPubKey,
      attestRpIdHash: rpIdHash,
      attestSignCount: signCount,
      // messageData.publicCert is guaranteed by verifyAttestationNonceBinding
      authCertPem: messageData.publicCert!,
      baseProps,
    });
    if (assertionRes instanceof IosPhaseFail) return fail(assertionRes);
    const {
      assertionAuthData,
      assertionRpIdHash,
      assertionFlags,
      assertionSignCount,
      signatureEncodingUsed,
      authCertThumbprintHex,
    } = assertionRes;

    trackEvent('IosAuth.AppAttestVerified', {
      ...baseProps,
      assertionSignCount,
      signatureEncoding: signatureEncodingUsed,
    });

    // ---- Build the decoded verdict for persistence. ----
    const durationMs = Date.now() - startTime;
    const credCert = new crypto.X509Certificate(credCertDer);
    const intCert = new crypto.X509Certificate(intermediateDer);
    const credCertThumbprint = crypto.createHash('sha256').update(credCertDer).digest('hex');
    const aaguidUtf8 = aaguid.toString('utf8').replace(/\0+$/, '');

    const verdict: AppAttestVerdict = {
      fmt,
      rpIdHash: rpIdHash.toString('hex'),
      appId: expectedAppId,
      aaguid: aaguidUtf8,
      flags,
      signCount,
      credentialId: credentialId.toString('hex'),
      credentialIdMatchesPubKey: true,
      credCert: {
        subject: credCert.subject,
        issuer: credCert.issuer,
        serialNumber: credCert.serialNumber,
        validFrom: credCert.validFrom,
        validTo: credCert.validTo,
        thumbprint: credCertThumbprint,
      },
      credCertPem: credCert.toString(),
      intermediateCert: {
        subject: intCert.subject,
        issuer: intCert.issuer,
        serialNumber: intCert.serialNumber,
        validFrom: intCert.validFrom,
        validTo: intCert.validTo,
      },
      receiptLength,
      receipt: receipt ? receipt.toString('base64') : null,
      authDataLength: authData.length,
      nonceExtension: certNonce.toString('hex'),
      expectedClientDataHash: challengeBytes.toString('hex'),
      authCertThumbprint: authCertThumbprintHex,
      nonceVerified: true,
      assertion: {
        authenticatorDataLength: assertionAuthData.length,
        rpIdHash: assertionRpIdHash.toString('hex'),
        flags: assertionFlags,
        signCount: assertionSignCount,
        signatureLength: parsedAssertion.signature.length,
        signatureEncoding: signatureEncodingUsed,
        expectedClientDataHash: authCertThumbprintHex,
        signatureVerified: true,
      },
      challengeBinding:
        'attest: sha256(authData || challengeHashBytes);  assert: ECDSA-SHA256(credCertPubKey, nonce = sha256(authenticatorData || sha256(authCertDER)))',
    };

    trackEvent(
      'IosAuth.VerifySuccess',
      {
        ...baseProps,
        rpIdHashPrefix: rpIdHash.toString('hex').slice(0, 12),
        credCertSubject: credCert.subject,
        aaguid: aaguidUtf8,
        flags,
        attestSignCount: signCount,
        assertSignCount: assertionSignCount,
        warningCount: warnings.length,
      },
      { durationMs },
    );

    return {
      verified: true,
      platform: 'ios',
      message: 'iOS App Attest verified successfully',
      timestamp: new Date().toISOString(),
      challengeHash: messageData.challengeHash,
      clientId: messageData.clientId,
      attestJsonLength: attestJson.length,
      chainLength: 2,
      rootCA: 'Apple App Attestation Root CA',
      appAttestVerdict: verdict,
      ...(warnings.length > 0 && { warnings }),
    };
  } catch (error) {
    trackException(error, { source: 'verifyiOSAuth.unexpected', ...baseProps });
    return failVerify(
      'UNEXPECTED_ERROR',
      `iOS attestation verification error: ${error instanceof Error ? error.message : 'unknown error'}`,
    );
  }
}
