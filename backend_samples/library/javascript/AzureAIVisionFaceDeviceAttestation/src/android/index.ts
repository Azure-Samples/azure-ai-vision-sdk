/**
 * Public entry point for the Android attestation module.
 *
 * Internal layout (kept small for review):
 *   types.ts                — AndroidAttestationJson, PlayIntegrityVerdict,
 *                             AndroidPhaseFail, BaseProps
 *   revocation.ts           — Google revocation list fetch + lookup
 *   keymaster_ext.ts        — Keymaster/KeyMint extension OID parser
 *   google_roots.ts         — pinned Google hardware attestation root CAs
 *   play_integrity_api.ts   — playintegrity.decodeIntegrityToken call
 *   integrity_checks.ts     — requestHash / timestamp / verdict policy
 *   chain_checks.ts         — parse, build, leaf-binding, validity,
 *                             chain signatures, revocation, session challenge
 *   verify.ts               — verifyAndroidAuth orchestrator
 */
export { verifyAndroidAuth } from './verify';
export type { PlayIntegrityVerdict } from './types';
