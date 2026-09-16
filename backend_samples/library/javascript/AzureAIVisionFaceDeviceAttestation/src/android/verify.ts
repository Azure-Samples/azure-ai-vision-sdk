/**
 * Top-level Android attestation orchestrator.
 *
 * Each verification step is one named phase in `chain_checks.ts` /
 * `integrity_checks.ts`; this file is just the sequencer + telemetry adapter.
 * Phases return their result payload on success, or an `AndroidPhaseFail`
 * carrying the reason code + extras — `failVerify` turns that into the
 * AuthVerificationResult the caller (auth_verification.verifyAuthBySystem)
 * already expects.
 */
import type { AuthVerificationResult, AttestationMessageData } from '../auth_verification';
import { trackEvent, trackException } from '../logging';
import { getAttestationConfig } from '../config';
import { AndroidPhaseFail, type BaseProps, type PlayIntegrityVerdict } from './types';
import {
  parseAndroidAttestJson,
  buildAndroidChainDerBuffers,
  verifyLeafBoundToChain0,
  validateAndroidChainValidityDates,
  verifyAndroidChainSignaturesAndRoot,
  verifyAttestationSecurityLevel,
  verifyAndroidChainRevocations,
  verifyChainBindsSessionChallenge,
} from './chain_checks';
import { decryptAndVerifyIntegrityVerdict } from './play_integrity_api';
import {
  verifyIntegrityRequestHash,
  verifyIntegrityTimestamp,
  evaluatePlayIntegrityVerdict,
} from './integrity_checks';

/**
 * Verify Android Key Attestation certificate chain + Play Integrity verdict.
 *
 * @param messageData - Parsed message object containing publicCert (leaf
 *                       certificate in PEM format)
 * @param attestJson - The attestation JSON string from the request (contains
 *                       token and certificateChain)
 * @returns Verification result object
 */
export async function verifyAndroidAuth(
  messageData: AttestationMessageData,
  attestJson: string,
): Promise<AuthVerificationResult> {
  const startTime = Date.now();
  const baseProps: BaseProps = {
    platform: 'android',
    clientId: messageData.clientId,
    challengeHashPrefix: messageData.challengeHash?.slice(0, 8),
    attestJsonLength: attestJson.length,
  };

  trackEvent('AndroidAuth.VerifyStart', baseProps);

  const failVerify = (
    reason: string,
    message: string,
    extras: Partial<AuthVerificationResult> = {},
    extraProps: Record<string, unknown> = {},
  ): AuthVerificationResult => {
    const durationMs = Date.now() - startTime;
    trackEvent(
      'AndroidAuth.VerifyFail',
      { ...baseProps, reason, ...extraProps },
      { durationMs },
    );
    return {
      verified: false,
      platform: 'android',
      message,
      timestamp: new Date().toISOString(),
      ...extras,
    };
  };

  const fail = (f: AndroidPhaseFail) =>
    failVerify(f.reason, f.message, f.extras, f.extraProps);

  try {
    const debugMode = getAttestationConfig().debugMode;

    // 1. parse + structural validation
    const attestData = parseAndroidAttestJson(attestJson, baseProps);
    if (attestData instanceof AndroidPhaseFail) return fail(attestData);

    // 2. build DER buffers from base64 chain + PEM leaf
    const built = buildAndroidChainDerBuffers(attestData, messageData.publicCert);
    if (built instanceof AndroidPhaseFail) return fail(built);
    const { certChainDer, leafCertDer } = built;

    // 3. leaf <-> chain[0] binding
    const leafBindFail = verifyLeafBoundToChain0(leafCertDer, certChainDer);
    if (leafBindFail) return fail(leafBindFail);

    // 4. validity dates (leaf warning, chain hard-fail — even in DEBUG_MODE)
    const validity = validateAndroidChainValidityDates(leafCertDer, certChainDer, baseProps);
    if (validity instanceof AndroidPhaseFail) return fail(validity);
    const leafCertValidityWarning = validity.leafCertValidityWarning;

    // 5. chain signatures + pinned root CA
    const chainSigRes = await verifyAndroidChainSignaturesAndRoot(certChainDer);
    if (chainSigRes instanceof AndroidPhaseFail) return fail(chainSigRes);
    const { rootCASubject } = chainSigRes;

    // 6. attestation must be issued by TEE or StrongBox
    const securityLevelFail = verifyAttestationSecurityLevel(certChainDer[0]);
    if (securityLevelFail) return fail(securityLevelFail);

    // 7. revocation list lookup (fails closed)
    const revFail = await verifyAndroidChainRevocations(leafCertDer, certChainDer);
    if (revFail) return fail(revFail);

    // 8. keymaster extension binds the session challenge
    const chainChallengeRes = verifyChainBindsSessionChallenge(
      certChainDer[0],
      messageData.challengeHash,
    );
    if (chainChallengeRes instanceof AndroidPhaseFail) return fail(chainChallengeRes);
    const { chainChallengeHex } = chainChallengeRes;

    // 9. Play Integrity verdict. Required, unless the API was unavailable /
    //    quota-exceeded and the operator opted into
    //    allowAndroidAttestationWhenGoogleUnavailable — in which case the
    //    hardware Key Attestation above (incl. the session-bound keymaster
    //    challenge in step 7) stands on its own and the verdict is skipped.
    if (!attestData.token) {
      return failVerify(
        'MISSING_INTEGRITY_TOKEN',
        'Play Integrity token is required but not provided',
      );
    }
    const integrityResult = await decryptAndVerifyIntegrityVerdict(attestData.token);

    const warnings: string[] = [];
    let integrityVerdict: PlayIntegrityVerdict | undefined;

    if (integrityResult.ok) {
      integrityVerdict = integrityResult.verdict;
    } else if (
      integrityResult.tolerable &&
      getAttestationConfig().allowAndroidAttestationWhenGoogleUnavailable
    ) {
      const warning = `Play Integrity verdict unavailable (${integrityResult.reason}); accepted on Key Attestation alone via allowAndroidAttestationWhenGoogleUnavailable`;
      warnings.push(warning);
      trackEvent('AndroidAuth.IntegrityUnavailableAccepted', {
        ...baseProps,
        reason: integrityResult.reason,
      });
    } else {
      return failVerify(
        'INTEGRITY_DECRYPT_FAIL',
        'Play Integrity verification failed: could not decrypt or verify integrity token',
      );
    }

    // verdict-derived summary; stays empty/false when the verdict was skipped.
    let appRecognition: string | undefined;
    let deviceVerdicts: string[] = [];
    let playProtect: string | undefined;
    let hasStrongIntegrity = false;
    let hasDeviceIntegrity = false;
    let hasBasicIntegrity = false;

    if (integrityVerdict) {
      // Persist the verified chain challenge on the verdict so downstream storage
      // (cert_thumb:* metadata via saveCertificate in attestation/register) has an audit
      // record of which session this attestation was bound to.
      integrityVerdict.attestationChallenge = chainChallengeHex;
      trackEvent('AndroidAuth.IntegrityVerdictReceived', {
        ...baseProps,
        hasRequestDetails: !!integrityVerdict.requestDetails,
        hasAppIntegrity: !!integrityVerdict.appIntegrity,
        hasDeviceIntegrity: !!integrityVerdict.deviceIntegrity,
        hasEnvironmentDetails: !!integrityVerdict.environmentDetails,
      });

      // 10. requestHash binds the integrity token to the auth key
      const requestHashFail = verifyIntegrityRequestHash(integrityVerdict, leafCertDer);
      if (requestHashFail) return fail(requestHashFail);

      // 11. timestamp freshness
      const timestampFail = verifyIntegrityTimestamp(integrityVerdict);
      if (timestampFail) return fail(timestampFail);

      // 12. appIntegrity + deviceIntegrity + environment warnings
      const verdictRes = evaluatePlayIntegrityVerdict(
        integrityVerdict,
        baseProps,
        debugMode,
        warnings,
      );
      if (verdictRes instanceof AndroidPhaseFail) return fail(verdictRes);
      ({
        appRecognition,
        deviceVerdicts,
        playProtect,
        hasStrongIntegrity,
        hasDeviceIntegrity,
        hasBasicIntegrity,
      } = verdictRes);
    }

    const durationMs = Date.now() - startTime;
    trackEvent(
      'AndroidAuth.VerifySuccess',
      {
        ...baseProps,
        chainLength: certChainDer.length + 1,
        rootCA: rootCASubject,
        integritySkipped: !integrityVerdict,
        appRecognition,
        deviceVerdicts: deviceVerdicts.join(',') || '(empty)',
        playProtect,
        hasStrongIntegrity,
        hasDeviceIntegrityFallback: hasDeviceIntegrity,
        hasBasicIntegrityFallback: hasBasicIntegrity,
        warningCount: warnings.length,
        leafCertValidityWarning: leafCertValidityWarning ?? null,
        chainChallengePrefix: chainChallengeHex.slice(0, 12),
      },
      { durationMs },
    );

    return {
      verified: true,
      platform: 'android',
      message: integrityVerdict
        ? 'Android Key Attestation and Play Integrity verified successfully'
        : 'Android Key Attestation verified successfully (Play Integrity unavailable, accepted by policy)',
      timestamp: new Date().toISOString(),
      challengeHash: messageData.challengeHash,
      clientId: messageData.clientId,
      attestJsonLength: attestJson.length,
      chainLength: certChainDer.length + 1, // +1 for leaf cert
      rootCA: rootCASubject,
      integrityVerdict,
      ...(leafCertValidityWarning && { leafCertValidityWarning }),
      ...(warnings.length > 0 && { warnings }),
    };
  } catch (error) {
    trackException(error, { source: 'verifyAndroidAuth.unexpected', ...baseProps });
    return failVerify(
      'UNEXPECTED_ERROR',
      `Android attestation verification error: ${error instanceof Error ? error.message : 'unknown error'}`,
    );
  }
}
