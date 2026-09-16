/**
 * Shared types for the iOS App Attest verifier.
 *
 * Public:
 *   AppAttestVerdict        — decoded attestation+assertion summary, persisted
 *                             in cert_thumb:* metadata.
 *   OngoingAssertionResult  — per-call assertion verify result.
 *
 * Internal:
 *   IosAttestJson, AppAttestObject, AppAttestAssertionObject — decoded shapes
 *   IosPhaseFail             — discriminated-union failure signal
 *   IosBaseProps             — telemetry property bag
 *   CborValue                — value type produced by the minimal CBOR decoder
 */
import type { AuthVerificationResult } from '../auth_verification';

/**
 * Decoded App Attest verdict — analog of PlayIntegrityVerdict. Threaded through
 * `verifyAuthBySystem` and persisted under `cert_thumb:{thumbprint}` metadata
 * by `saveCertificate` so the post-attestation record retains everything we
 * decoded from the token.
 */
export interface AppAttestVerdict {
  fmt: string;
  /** lowercase hex */
  rpIdHash: string;
  /** matched IOS_APP_ID (TeamID.BundleID) */
  appId: string;
  /** "appattest" (prod) or "appattestdevelop" */
  aaguid: string;
  flags: number;
  signCount: number;
  /** lowercase hex */
  credentialId: string;
  credentialIdMatchesPubKey: boolean;
  credCert: {
    subject: string;
    issuer: string;
    serialNumber: string;
    validFrom: string;
    validTo: string;
    /** lowercase hex SHA-256 */
    thumbprint: string;
  };
  /**
   * credCert in PEM form. Persisted so subsequent calls (attestation/verify,
   * session/token, liveness/digest) can re-derive the public key and verify
   * App Attest assertions bound to those calls without re-running the full
   * attestation chain. See verifyIosOngoingAssertion.
   */
  credCertPem: string;
  intermediateCert: {
    subject: string;
    issuer: string;
    serialNumber: string;
    validFrom: string;
    validTo: string;
  };
  /** Apple App Attest receipt blob length (bytes). */
  receiptLength: number;
  /**
   * Apple App Attest receipt (base64), or null if the attestation carried none.
   * Persisted so it can later be exchanged with Apple's App Attest data endpoint
   * (DeviceCheck) to read the device fraud risk metric.
   */
  receipt: string | null;
  /** authData length (bytes) */
  authDataLength: number;
  /** nonce extension OCTET STRING value, lowercase hex */
  nonceExtension: string;
  /** The clientDataHash we expected for the attestation; lowercase hex */
  expectedClientDataHash: string;
  /** Auth cert thumbprint (the messageData.publicCert) used in the assertion binding */
  authCertThumbprint: string;
  nonceVerified: boolean;
  /** The assertion proof bundled alongside the attestation. */
  assertion: {
    /** length of the authenticatorData blob (bytes) */
    authenticatorDataLength: number;
    /** authenticatorData.rpIdHash (lowercase hex) — must equal verdict.rpIdHash */
    rpIdHash: string;
    flags: number;
    signCount: number;
    /** signature length (bytes) */
    signatureLength: number;
    /** ECDSA signature encoding that verified — 'der' (Apple's documented format)
     *  or 'ieee-p1363' (raw r||s, used as fallback). */
    signatureEncoding: 'der' | 'ieee-p1363';
    /** What we expected the clientDataHash to be; lowercase hex (= sha256(authCertDER) bytes) */
    expectedClientDataHash: string;
    /** Did the credCert pubkey verify the assertion signature? */
    signatureVerified: boolean;
  };
  challengeBinding: string;
}

/**
 * Result of an ongoing-assertion (per-call) verify.
 */
export interface OngoingAssertionResult {
  ok: boolean;
  /** Telemetry-style reason code on failure. */
  reason?: string;
  /** Human-readable message on failure. */
  message?: string;
  /** signCount parsed off authenticatorData, returned even on signature-fail so callers can log it. */
  signCount?: number;
  /** Encoding that verified — 'der' is Apple's documented format; 'ieee-p1363' is fallback. */
  signatureEncoding?: 'der' | 'ieee-p1363';
}

/**
 * Outer JSON shape posted by the iOS client.
 */
export interface IosAttestJson {
  /** base64-encoded CBOR result of DCAppAttestService.attestKey */
  attestation: string;
  /** base64-encoded CBOR result of DCAppAttestService.generateAssertion */
  assertion: string;
}

/**
 * Decoded shape of the CBOR `attestation` object.
 */
export interface AppAttestObject {
  fmt: string;
  authData: Buffer;
  credCertDer: Buffer;
  intermediateDer: Buffer;
  receiptLength: number;
  /** Raw Apple App Attest receipt bytes (attStmt.receipt), or null if absent. */
  receipt: Buffer | null;
}

/**
 * Decoded shape of the CBOR `assertion` object.
 */
export interface AppAttestAssertionObject {
  signature: Buffer;
  authenticatorData: Buffer;
}

/**
 * Failure signal returned by a phase helper. The main verifier catches these
 * and feeds them through failVerify so telemetry stays uniform.
 */
export class IosPhaseFail {
  constructor(
    public readonly reason: string,
    public readonly message: string,
    public readonly extras: Partial<AuthVerificationResult> = {},
    public readonly extraProps: Record<string, unknown> = {},
  ) {}
}

/**
 * Telemetry property bag passed through phases — augments App Insights events
 * with platform/clientId/challengeHashPrefix/attestJsonLength.
 */
export type IosBaseProps = Record<string, unknown>;

/**
 * Value type produced by the minimal CBOR decoder. Apple's App Attest payloads
 * only ever use these.
 */
export type CborValue =
  | number
  | string
  | Buffer
  | CborValue[]
  | Map<string | number, CborValue>;
