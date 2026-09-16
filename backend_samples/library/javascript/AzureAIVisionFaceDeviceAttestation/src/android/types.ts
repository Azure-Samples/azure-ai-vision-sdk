/**
 * Shared types for the Android Key Attestation + Play Integrity verifier.
 *
 * - `AndroidAttestationJson` is what the client posts (token + chain).
 * - `PlayIntegrityVerdict` is the decoded Play Integrity payload; exported so
 *   `saveCertificate` can persist it in the cert_thumb:* metadata.
 * - `AndroidPhaseFail` is the discriminated-union failure signal returned by
 *   every phase helper; the main verifier turns it into `failVerify`.
 * - `BaseProps` is the redacted telemetry property bag threaded through phases.
 */
import type { AuthVerificationResult } from '../auth_verification';
import { z } from 'zod';

/**
 * Android attestation JSON structure (what the client posts).
 */
export const androidAttestationSchema = z.looseObject({
  certificateChain: z.array(z.string()).min(1),
  token: z.string().nullish(),
});

export type AndroidAttestationJson = z.infer<typeof androidAttestationSchema>;

/**
 * Play Integrity verdict response structure
 */
export interface PlayIntegrityVerdict {
  requestDetails?: {
    requestPackageName?: string;
    timestampMillis?: string;
    requestHash?: string;
  };
  accountDetails?: {
    appLicensingVerdict?: string;
  };
  appIntegrity?: {
    appRecognitionVerdict?: string;
    packageName?: string;
    certificateSha256Digest?: string[];
    versionCode?: string;
  };
  deviceIntegrity?: {
    deviceRecognitionVerdict?: string[];
    recentDeviceActivity?: {
      deviceActivityLevel?: string;
    };
    deviceAttributes?: {
      sdkVersion?: number;
    };
  };
  environmentDetails?: {
    playProtectVerdict?: string;
    appAccessRiskVerdict?: {
      appsDetected?: string[];
    };
  };
  /**
   * Lowercase hex of the `attestationChallenge` OCTET STRING extracted from
   * the leaf cert's keymaster extension (OID 1.3.6.1.4.1.11129.2.1.17).
   * Populated server-side after the chain challenge is verified to equal
   * `messageData.challengeHash`, so the persisted verdict carries proof of
   * the session binding for audit.
   */
  attestationChallenge?: string;
}

/**
 * Failure signal returned by a phase helper. The main verifier catches these
 * and feeds them through failVerify so telemetry stays uniform.
 */
export class AndroidPhaseFail {
  constructor(
    public readonly reason: string,
    public readonly message: string,
    public readonly extras: Partial<AuthVerificationResult> = {},
    public readonly extraProps: Record<string, unknown> = {},
  ) {}
}

/**
 * Redacted telemetry property bag passed through phases.
 */
export type BaseProps = Record<string, unknown>;
