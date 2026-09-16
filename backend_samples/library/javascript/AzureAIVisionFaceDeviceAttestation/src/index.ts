/**
 * Public API of the attestation library — the ONLY module a host should import.
 *
 * Usage:
 *   1. Build the service once at startup:
 *        const svc = createAttestationService(config, store, logger)
 *      providing a {@link ClusterStore} + {@link AttestationLogger} implementation
 *      and an {@link AttestationConfig}.
 *   2. In each API route: parse the incoming request into the matching typed
 *      request (e.g. {@link AttestationChallengeRequest}), call the service
 *      method, and serialize the returned {@link HandlerOutcome}.
 *   3. Serve the two /.well-known documents from `svc.appleAppSiteAssociation()`
 *      / `svc.androidAssetLinks()`.
 *
 * Everything else under this folder is internal implementation.
 */

// --- Construction + service surface ---
export { createAttestationService, AttestationService } from './service';
export type { AttestationConfig } from './config';

// --- Dependencies the host implements + injects ---
export type { ClusterStore, SessionRecord, CertificateData, Snapshot, UpdateResult } from './store';
export { StorageError } from './store';
export type { AttestationLogger, TelemetryProps } from './logging';

// --- Result shape returned by every service route method ---
export type { HandlerOutcome, ErrorBody } from './handlers/types';

// --- Per-endpoint request / body / response types (host builds the request) ---
export type {
  AttestationChallengeRequest,
  AttestationChallengeResponse,
} from './handlers/challenge';
export type {
  AttestationRegisterRequest,
  AttestationRegisterBody,
  AttestationRegisterResponse,
  AttestationRegisterData,
} from './handlers/register';
export type {
  AttestationVerifyRequest,
  AttestationVerifyBody,
  AttestationVerifyResponse,
} from './handlers/verify';
export type {
  SessionTokenRequest,
  SessionTokenBody,
  SessionTokenResponse,
} from './handlers/token';
export type {
  LivenessDigestRequest,
  LivenessDigestBody,
  LivenessDigestResponse,
} from './handlers/digest';

// --- Canonical route paths (use as telemetry tags in the host) ---
import { ROUTE as challengeRoute } from './handlers/challenge';
import { ROUTE as registerRoute } from './handlers/register';
import { ROUTE as verifyRoute } from './handlers/verify';
import { ROUTE as sessionTokenRoute } from './handlers/token';
import { ROUTE as livenessDigestRoute } from './handlers/digest';

export const ROUTES = {
  challenge: challengeRoute,
  register: registerRoute,
  verify: verifyRoute,
  sessionToken: sessionTokenRoute,
  livenessDigest: livenessDigestRoute,
} as const;
