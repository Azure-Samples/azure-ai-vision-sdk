/**
 * Per-call App Attest assertion verifier used after registration.
 *
 * Registration (verifyiOSAuth) runs the full attestation chain once. Every
 * subsequent authenticated call (attestation/verify / session/token / liveness/digest) re-runs
 * a fresh assertion against the persisted credCert's public key:
 *
 *   clientDataHash = SHA-256(blob)               // blob = bytes the call signs
 *   nonce          = SHA-256(authData || clientDataHash)
 *   ECDSA_with_SHA256_verify(credCertPubKey, signature, nonce) == true
 *
 * The auth-cert ECDSA signature alone proves possession of the key but not
 * Apple-hardware provenance; this assertion binds Apple back into the loop.
 * signCount monotonicity prevents replay.
 *
 * `getExpectedIosRpIdHash` caches SHA-256(IOS_APP_ID) per process because both
 * registration and every ongoing call need it.
 */
import crypto from 'crypto';
import { parseAppAttestAssertion, parseAssertionAuthData } from './parsers';
import { getAttestationConfig } from '../config';
import type { AppAttestAssertionObject, OngoingAssertionResult } from './types';

let cachedExpectedRpIdHash: { appId: string; hash: Buffer } | null = null;

/**
 * SHA-256(IOS_APP_ID) — used by both verifyiOSAuth (registration) and
 * verifyIosOngoingAssertion (every subsequent call). Cached per process so
 * we don't rehash the same string on every request.
 */
export function getExpectedIosRpIdHash(): Buffer | null {
  const appId = getAttestationConfig().iosAppId;
  if (!appId) return null;
  if (cachedExpectedRpIdHash?.appId === appId) return cachedExpectedRpIdHash.hash;
  const hash = crypto.createHash('sha256').update(appId, 'utf8').digest();
  cachedExpectedRpIdHash = { appId, hash };
  return hash;
}

/**
 * Verify a fresh App Attest assertion produced by DCAppAttestService.generateAssertion
 * against the credCert that was persisted at registration time. Used by
 * attestation/verify / session/token / liveness/digest to re-exercise Apple hardware on every
 * authenticated call (the auth-cert ECDSA signature alone proves possession of
 * the key but not provenance; this binds Apple back into the loop).
 *
 *   clientDataHash = SHA-256(blob)            // blob = bytes the auth-cert sig signs
 *   nonce          = SHA-256(authData || clientDataHash)
 *   ECDSA_with_SHA256_verify(credCertPubKey, signature, nonce) == true
 *
 * @param credCertPem    PEM credCert from the AppAttestVerdict persisted at registration.
 * @param blob           Exact bytes the auth-cert signature signs on this call.
 * @param assertionB64   Base64 CBOR result of DCAppAttestService.generateAssertion.
 * @param expectedRpIdHash  SHA-256(IOS_APP_ID), from getExpectedIosRpIdHash().
 * @param lastSignCount  Highest signCount seen for this credCert (registration-time count
 *                       on first call). signCount must be strictly greater.
 */
export function verifyIosOngoingAssertion(
  credCertPem: string,
  blob: Buffer,
  assertionB64: string,
  expectedRpIdHash: Buffer,
  lastSignCount: number,
): OngoingAssertionResult {
  let credCertPubKey: crypto.KeyObject;
  try {
    credCertPubKey = new crypto.X509Certificate(credCertPem).publicKey;
  } catch (e) {
    return {
      ok: false,
      reason: 'CRED_CERT_PARSE_FAIL',
      message: `Failed to parse persisted credCert PEM: ${e instanceof Error ? e.message : 'unknown'}`,
    };
  }

  let parsed: AppAttestAssertionObject;
  try {
    parsed = parseAppAttestAssertion(assertionB64);
  } catch (e) {
    return {
      ok: false,
      reason: 'ASSERTION_DECODE_ERROR',
      message: `Failed to decode App Attest assertion: ${e instanceof Error ? e.message : 'unknown'}`,
    };
  }

  const authData = parsed.authenticatorData;
  const { rpIdHash, signCount } = parseAssertionAuthData(authData);

  if (!rpIdHash.equals(expectedRpIdHash)) {
    return {
      ok: false,
      reason: 'ASSERTION_RPID_MISMATCH',
      message: 'Assertion rpIdHash does not match SHA-256(IOS_APP_ID)',
      signCount,
    };
  }

  if (signCount <= lastSignCount) {
    return {
      ok: false,
      reason: 'ASSERTION_SIGNCOUNT_NOT_INCREMENTED',
      message: `Assertion signCount (${signCount}) is not greater than lastSignCount (${lastSignCount})`,
      signCount,
    };
  }

  const clientDataHash = crypto.createHash('sha256').update(blob).digest();
  const nonce = crypto
    .createHash('sha256')
    .update(authData)
    .update(clientDataHash)
    .digest();

  let signatureEncoding: 'der' | 'ieee-p1363' = 'der';
  let signatureOk = false;
  try {
    signatureOk = crypto.verify('sha256', nonce, credCertPubKey, parsed.signature);
    if (!signatureOk) {
      signatureEncoding = 'ieee-p1363';
      signatureOk = crypto.verify(
        'sha256',
        nonce,
        { key: credCertPubKey, dsaEncoding: 'ieee-p1363' },
        parsed.signature,
      );
    }
  } catch (e) {
    return {
      ok: false,
      reason: 'ASSERTION_VERIFY_EXCEPTION',
      message: `Assertion ECDSA verify threw: ${e instanceof Error ? e.message : 'unknown'}`,
      signCount,
    };
  }

  if (!signatureOk) {
    return {
      ok: false,
      reason: 'ASSERTION_SIGNATURE_INVALID',
      message: 'Assertion signature does not verify against credCert public key',
      signCount,
    };
  }

  return { ok: true, signCount, signatureEncoding };
}
