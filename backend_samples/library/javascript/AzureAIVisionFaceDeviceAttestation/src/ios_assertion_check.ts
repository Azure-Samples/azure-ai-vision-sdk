/**
 * Shared "verify a fresh App Attest assertion + advance the persisted
 * signCount" helper used by every iOS-aware route after registration
 * (attestation/verify, session/token, liveness/digest).
 *
 * Each of those routes ran an identical ~80-line block. This module collapses
 * it into one call. The route still owns its own thumbprint resolution
 * (attestation/verify computes it from the client-supplied authPublicCert; the
 * others read it from session storage) and just passes thumbprint + assertion
 * + the bytes the assertion signs.
 *
 * Returns either {ok:true} (caller continues) or {ok:false, response}, where
 * `response` is the exact 401 / 500 the route would have returned inline.
 * Telemetry is emitted under the caller's route name via trackApiFail.
 *
 * No behavior change vs. the inlined version it replaces — same reason codes,
 * same status codes, same response messages.
 */

import { getCertificate, updateCertificateMetadata } from './cert_store';
import {
  AppAttestVerdict,
  getExpectedIosRpIdHash,
  verifyIosOngoingAssertion,
} from './ios';
import { trackApiFail } from './api_telemetry';
import type { ClusterStore } from './store';
import { fail, type HandlerOutcome, type ErrorBody } from './handlers/types';

export interface IosAssertionCheckArgs {
  /** Caller route name used as the telemetry route tag. */
  routeName: string;
  /** Session UUID, threaded into telemetry properties. */
  sessionId: string;
  /** SHA-256 thumbprint used to look up the stored authentication certificate. */
  thumbprint: string;
  /** Exact bytes the App Attest assertion's nonce binds. */
  blob: Buffer;
  /** base64 CBOR result of DCAppAttestService.generateAssertion. */
  assertion: string | undefined;
}

export type IosAssertionCheckResult =
  | { ok: true; commit: () => Promise<boolean> }
  | { ok: false; result: HandlerOutcome<ErrorBody, never> };

export async function checkIosAssertionForRoute(
  store: ClusterStore,
  args: IosAssertionCheckArgs,
): Promise<IosAssertionCheckResult> {
  const { routeName, sessionId, thumbprint, blob, assertion } = args;

  if (!assertion || typeof assertion !== 'string') {
    return {
      ok: false,
      result: fail(routeName, 401, 'MISSING_ASSERTION', 'Missing iOS App Attest assertion', {
        properties: { sid: sessionId, thumbprint },
      }),
    };
  }

  const certRecord = await getCertificate(store, thumbprint);
  if (!certRecord) {
    return {
      ok: false,
      result: fail(routeName, 401, 'CERT_RECORD_MISSING', 'Certificate record not found', {
        properties: { sid: sessionId, thumbprint },
      }),
    };
  }
  const certMetadata = certRecord.value.metadata as Record<string, unknown> | undefined;
  const verdict = certMetadata?.appAttestVerdict as AppAttestVerdict | undefined;
  if (!verdict?.credCertPem) {
    // Pre-change cert records (registered before credCertPem was persisted)
    // need re-registration to populate it. Cert TTL bounds this to <=7 days.
    trackApiFail(routeName, 'LEGACY_CERT_NO_CREDCERT_PEM', 401, { sid: sessionId, thumbprint });
    return {
      ok: false,
      result: fail(
        routeName,
        401,
        'LEGACY_CERT_NO_CREDCERT_PEM',
        'Certificate predates assertion requirement; re-registration required',
      ),
    };
  }

  const expectedRpIdHash = getExpectedIosRpIdHash();
  if (!expectedRpIdHash) {
    return {
      ok: false,
      result: fail(routeName, 500, 'MISSING_IOS_APP_ID', 'Server misconfigured: IOS_APP_ID not set', {
        properties: { sid: sessionId },
      }),
    };
  }

  const lastSignCount =
    typeof certMetadata?.lastAssertionSignCount === 'number'
      ? (certMetadata.lastAssertionSignCount as number)
      : (verdict.assertion?.signCount ?? 0);

  const assertionResult = verifyIosOngoingAssertion(
    verdict.credCertPem,
    blob,
    assertion,
    expectedRpIdHash,
    lastSignCount,
  );

  if (!assertionResult.ok) {
    return {
      ok: false,
      result: fail(routeName, 401, 'ASSERTION_VERIFY_FAIL', 'iOS assertion verification failed', {
        properties: {
          sid: sessionId,
          thumbprint,
          reason: assertionResult.reason,
          message: assertionResult.message,
          signCount: assertionResult.signCount,
          lastSignCount,
        },
      }),
    };
  }

  return {
    ok: true,
    commit: () => updateCertificateMetadata(store, thumbprint, {
      lastAssertionSignCount: assertionResult.signCount,
    }, certRecord),
  };
}
