/**
 * Public entry point for the iOS App Attest module.
 *
 * Internal layout (kept small for review):
 *   types.ts                — AppAttestVerdict, OngoingAssertionResult,
 *                             IosPhaseFail, internal shapes
 *   constants.ts            — Apple root CA, aaguids, nonce OID
 *   cbor.ts                 — minimal CBOR decoder
 *   parsers.ts              — App Attest token + assertion decoders,
 *                             credCert nonce-extension extractor
 *   ongoing_assertion.ts    — verifyIosOngoingAssertion + cached rpIdHash
 *   attestation_phases.ts   — per-phase helpers used at registration
 *   verify.ts               — verifyiOSAuth orchestrator
 */
export { verifyiOSAuth } from './verify';
export {
  getExpectedIosRpIdHash,
  verifyIosOngoingAssertion,
} from './ongoing_assertion';
export type { AppAttestVerdict, OngoingAssertionResult } from './types';
