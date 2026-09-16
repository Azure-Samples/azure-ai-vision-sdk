/**
 * Semantic checks on the decoded Play Integrity verdict. Each helper covers
 * one logical check and returns either `null`/success-data or an
 * `AndroidPhaseFail` with the right reason code + extras.
 *
 *   verifyIntegrityRequestHash    — hex(sha256(leafCertDer)) == verdict.requestDetails.requestHash
 *   verifyIntegrityTimestamp      — token ≤ 5 min old
 *   evaluatePlayIntegrityVerdict  — package name, appRecognitionVerdict,
 *                                   deviceRecognitionVerdict policy, and
 *                                   collects Play Protect / app-access warnings
 */
import crypto from 'crypto';
import { trackEvent } from '../logging';
import { getAttestationConfig } from '../config';
import { AndroidPhaseFail, type BaseProps, type PlayIntegrityVerdict } from './types';

/**
 * Phase 8: requestHash binds the integrity token to the StrongBox-backed auth
 * key via its cert thumbprint:
 *   requestHash = hex( sha256(leafCertDer) )
 *
 * The session-binding proof lives in the keymaster extension on the chain
 * (verified earlier), so the integrity nonce only needs to bind the key. A
 * token captured under a different key still cannot be replayed because the
 * thumbprint binding holds.
 */
export function verifyIntegrityRequestHash(
  integrityVerdict: PlayIntegrityVerdict,
  leafCertDer: Buffer,
): null | AndroidPhaseFail {
  const expectedRequestHash = crypto.createHash('sha256').update(leafCertDer).digest('hex');
  const verdictRequestHash = integrityVerdict.requestDetails?.requestHash;
  if (verdictRequestHash !== expectedRequestHash) {
    trackEvent('AndroidAuth.IntegrityRequestHashMismatch', {
      expectedPrefix: expectedRequestHash.slice(0, 8),
      actualPrefix: verdictRequestHash?.slice(0, 8),
    });
    return new AndroidPhaseFail(
      'INTEGRITY_REQUEST_HASH_MISMATCH',
      'Play Integrity verification failed: requestHash mismatch (possible replay attack)',
      { integrityVerdict },
    );
  }
  return null;
}

/**
 * Phase 9: timestamp recency. A timestamp is required and tokens more than
 * 5 minutes from server time are rejected.
 */
export function verifyIntegrityTimestamp(
  integrityVerdict: PlayIntegrityVerdict,
): null | AndroidPhaseFail {
  const timestampMillis = integrityVerdict.requestDetails?.timestampMillis;
  const tokenTimestamp = Number(timestampMillis);
  if (!timestampMillis || !Number.isSafeInteger(tokenTimestamp)) {
    return new AndroidPhaseFail(
      'INTEGRITY_TIMESTAMP_INVALID',
      'Play Integrity verification failed: token timestamp missing or invalid',
      { integrityVerdict },
    );
  }
  const now = Date.now();
  const fiveMinutes = 5 * 60 * 1000;
  if (Math.abs(now - tokenTimestamp) > fiveMinutes) {
    trackEvent('AndroidAuth.IntegrityTimestampOld', {
      tokenTimestamp: new Date(tokenTimestamp).toISOString(),
      serverTimestamp: new Date(now).toISOString(),
      skewMs: Math.abs(now - tokenTimestamp),
    });
    return new AndroidPhaseFail(
      'INTEGRITY_TIMESTAMP_OLD',
      'Play Integrity verification failed: token timestamp too old',
      { integrityVerdict },
      {
        tokenTimestamp: new Date(tokenTimestamp).toISOString(),
        skewMs: Math.abs(now - tokenTimestamp),
      },
    );
  }
  return null;
}

/**
 * Phase 10: validate appIntegrity (package name + appRecognitionVerdict),
 * deviceIntegrity (STRONG / DEVICE / BASIC selectable via env), and collect
 * non-blocking environmentDetails warnings (Play Protect, App Access Risk).
 *
 * `warningsSink` is mutated in place with debug-mode appRecognition warnings
 * and the non-blocking environment warnings.
 */
export function evaluatePlayIntegrityVerdict(
  integrityVerdict: PlayIntegrityVerdict,
  baseProps: BaseProps,
  debugMode: boolean,
  warningsSink: string[],
): {
  appRecognition: string | undefined;
  deviceVerdicts: string[];
  playProtect: string | undefined;
  hasStrongIntegrity: boolean;
  hasDeviceIntegrity: boolean;
  hasBasicIntegrity: boolean;
} | AndroidPhaseFail {
  const appIntegrity = integrityVerdict.appIntegrity;
  const deviceIntegrity = integrityVerdict.deviceIntegrity;
  const environmentDetails = integrityVerdict.environmentDetails;

  trackEvent('AndroidAuth.PlayIntegrityVerdictEvaluated', {
    appRecognitionVerdict: appIntegrity?.appRecognitionVerdict,
    deviceRecognitionVerdicts: deviceIntegrity?.deviceRecognitionVerdict?.join(','),
    packageName: appIntegrity?.packageName,
    playProtectVerdict: environmentDetails?.playProtectVerdict,
  });

  const expectedPackageName = getAttestationConfig().androidPackageName;
  if (!expectedPackageName) {
    return new AndroidPhaseFail(
      'MISSING_PACKAGE_NAME_ENV',
      'ANDROID_PACKAGE_NAME environment variable not set',
    );
  }

  if (appIntegrity?.packageName !== expectedPackageName) {
    trackEvent('AndroidAuth.PackageNameMismatch', {
      expectedPackageName,
      actualPackageName: appIntegrity?.packageName,
    });
    return new AndroidPhaseFail(
      'PACKAGE_NAME_MISMATCH',
      'Play Integrity verification failed: package name mismatch',
      { integrityVerdict },
    );
  }

  // appRecognitionVerdict. In debug mode, UNRECOGNIZED_VERSION is downgraded
  // to a warning so sideloaded/internal builds not yet known to Play can still
  // verify.
  if (appIntegrity?.appRecognitionVerdict !== 'PLAY_RECOGNIZED') {
    if (debugMode && appIntegrity?.appRecognitionVerdict === 'UNRECOGNIZED_VERSION') {
      const warning = `App recognition warning (debug mode): ${appIntegrity?.appRecognitionVerdict}`;
      warningsSink.push(warning);
      trackEvent('AndroidAuth.AppRecognitionWarning', {
        ...baseProps,
        appRecognitionVerdict: appIntegrity?.appRecognitionVerdict,
      });
    } else {
      trackEvent('AndroidAuth.AppNotRecognized', {
        appRecognitionVerdict: appIntegrity?.appRecognitionVerdict,
      });
      return new AndroidPhaseFail(
        'APP_NOT_RECOGNIZED',
        `Play Integrity verification failed: app not recognized (${appIntegrity?.appRecognitionVerdict})`,
        { integrityVerdict },
        { appRecognitionVerdict: appIntegrity?.appRecognitionVerdict },
      );
    }
  }

  // deviceIntegrity. Default policy requires MEETS_STRONG_INTEGRITY
  // (hardware-backed key attestation + verified boot). MEETS_DEVICE_INTEGRITY
  // is accepted when ALLOW_DEVICE_INTEGRITY=true OR DEBUG_MODE=true;
  // MEETS_BASIC_INTEGRITY is accepted when ALLOW_BASIC_INTEGRITY=true OR
  // DEBUG_MODE=true. Both are intended for dev/testing.
  const deviceVerdicts = deviceIntegrity?.deviceRecognitionVerdict || [];
  const hasStrongIntegrity = deviceVerdicts.includes('MEETS_STRONG_INTEGRITY');

  const allowDeviceIntegrity = getAttestationConfig().allowDeviceIntegrity || debugMode;
  const hasDeviceIntegrity =
    allowDeviceIntegrity && deviceVerdicts.includes('MEETS_DEVICE_INTEGRITY');

  const allowBasicIntegrity = getAttestationConfig().allowBasicIntegrity || debugMode;
  const hasBasicIntegrity =
    allowBasicIntegrity && deviceVerdicts.includes('MEETS_BASIC_INTEGRITY');

  if (!hasStrongIntegrity && !hasDeviceIntegrity && !hasBasicIntegrity) {
    trackEvent('AndroidAuth.DeviceIntegrityFailed', {
      deviceRecognitionVerdicts: deviceVerdicts.join(',') || '(empty)',
      allowDeviceIntegrity,
      allowBasicIntegrity,
    });
    return new AndroidPhaseFail(
      'DEVICE_INTEGRITY_FAIL',
      'Play Integrity verification failed: device does not meet integrity requirements',
      { integrityVerdict },
      {
        deviceVerdicts: deviceVerdicts.join(',') || '(empty)',
        allowDeviceIntegrity,
        allowBasicIntegrity,
      },
    );
  }

  // Surface develop-build acceptance: STRONG wasn't met but DEVICE/BASIC was
  // allowed through (via env flag or DEBUG_MODE). Logged + collected so the
  // verdict response carries it.
  if (!hasStrongIntegrity && hasDeviceIntegrity) {
    const w = `Device integrity warning${debugMode ? ' (debug mode)' : ''}: MEETS_DEVICE_INTEGRITY only, not MEETS_STRONG_INTEGRITY`;
    warningsSink.push(w);
    trackEvent('AndroidAuth.DeviceIntegrityWarning', {
      ...baseProps,
      deviceVerdicts: deviceVerdicts.join(',') || '(empty)',
      acceptedAs: 'MEETS_DEVICE_INTEGRITY',
      debugMode,
    });
  } else if (!hasStrongIntegrity && hasBasicIntegrity) {
    const w = `Device integrity warning${debugMode ? ' (debug mode)' : ''}: MEETS_BASIC_INTEGRITY only, not MEETS_STRONG_INTEGRITY`;
    warningsSink.push(w);
    trackEvent('AndroidAuth.DeviceIntegrityWarning', {
      ...baseProps,
      deviceVerdicts: deviceVerdicts.join(',') || '(empty)',
      acceptedAs: 'MEETS_BASIC_INTEGRITY',
      debugMode,
    });
  }

  // Play Protect status (warning only, not blocking)
  if (environmentDetails?.playProtectVerdict !== 'NO_ISSUES') {
    const playProtectWarning = `Play Protect verdict: ${environmentDetails?.playProtectVerdict}`;
    warningsSink.push(playProtectWarning);
    trackEvent('AndroidAuth.PlayProtectWarning', {
      ...baseProps,
      playProtectVerdict: environmentDetails?.playProtectVerdict,
    });
  }

  // App Access Risk (warning only, not blocking)
  if (
    environmentDetails?.appAccessRiskVerdict?.appsDetected &&
    environmentDetails.appAccessRiskVerdict.appsDetected.length > 0
  ) {
    const appAccessWarning = `App Access Risk - detected apps: ${environmentDetails.appAccessRiskVerdict.appsDetected.join(', ')}`;
    warningsSink.push(appAccessWarning);
    trackEvent('AndroidAuth.AppAccessRiskWarning', {
      ...baseProps,
      detectedAppCount: environmentDetails.appAccessRiskVerdict.appsDetected.length,
    });
  }

  return {
    appRecognition: appIntegrity?.appRecognitionVerdict,
    deviceVerdicts,
    playProtect: environmentDetails?.playProtectVerdict,
    hasStrongIntegrity,
    hasDeviceIntegrity,
    hasBasicIntegrity,
  };
}
