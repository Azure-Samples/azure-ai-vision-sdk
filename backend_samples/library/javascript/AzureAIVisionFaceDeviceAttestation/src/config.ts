/**
 * Attestation runtime configuration.
 *
 * The library is configured ONCE at startup with an {@link AttestationConfig}
 * (see `createAttestationService`) and never reads `process.env` itself — so it
 * can be extracted and reused in any host that supplies these values. The app is
 * responsible for sourcing them (from env, a secret store, etc.).
 *
 * NOTE: telemetry is injected separately as an `AttestationLogger` (see
 * ./logging), not read from env. With config + logger injected, the library
 * reads no environment variables at all.
 */

export interface AttestationConfig {
  /** Max accepted client certificate size in bytes (was MAX_CERT_SIZE, default 10240). */
  maxCertSize: number;

  // --- Android Play Integrity ---
  /** Expected Android application id (was ANDROID_PACKAGE_NAME). */
  androidPackageName: string;
  /** Google service-account JSON for the Play Integrity API (was GOOGLE_SERVICE_ACCOUNT_JSON). */
  googleServiceAccountJson: string;
  /** Loosen recognition/integrity requirements for dev/testing (was DEBUG_MODE). */
  debugMode: boolean;
  /** Accept MEETS_DEVICE_INTEGRITY verdicts (was ALLOW_DEVICE_INTEGRITY). */
  allowDeviceIntegrity: boolean;
  /** Accept MEETS_BASIC_INTEGRITY verdicts (was ALLOW_BASIC_INTEGRITY). */
  allowBasicIntegrity: boolean;
  /** Optional: allow Android attestation to pass on hardware Key Attestation alone when the Play Integrity API is unavailable (env ALLOW_ANDROID_ATTESTATION_WHEN_GOOGLE_UNAVAILABLE, default false = fail closed). */
  allowAndroidAttestationWhenGoogleUnavailable?: boolean;

  // --- iOS App Attest ---
  /** iOS App Attest identity "<TeamID>.<BundleID>" (was IOS_APP_ID). */
  iosAppId: string;

  // --- Well-known / app-link binding ---
  /** iOS AASA appID; falls back to iosAppId when unset (was IOS_APPLINK_APP_ID). */
  iosApplinkAppId?: string;
  /** Optional App Clip appID "<TeamID>.<BundleID>.Clip" (was IOS_APP_CLIP_ID). */
  iosAppClipId?: string;
  /** Android signing-cert SHA-256 fingerprints for assetlinks (was ANDROID_SHA256_CERT_FINGERPRINTS). */
  androidSha256CertFingerprints: string[];
  /** Universal/App Link path pattern (was APPLINK_PATH, default /native*). */
  applinkPath: string;
}

let activeConfig: AttestationConfig | null = null;

/** Install the runtime config. Called once by `createAttestationService`. */
export function configureAttestation(config: AttestationConfig): void {
  activeConfig = config;
}

/**
 * The active {@link AttestationConfig}. Throws if the library has not been
 * configured yet (i.e. `createAttestationService` was never called).
 */
export function getAttestationConfig(): AttestationConfig {
  if (!activeConfig) {
    throw new Error(
      'Attestation library is not configured. Call createAttestationService(config, store) at startup.',
    );
  }
  return activeConfig;
}
