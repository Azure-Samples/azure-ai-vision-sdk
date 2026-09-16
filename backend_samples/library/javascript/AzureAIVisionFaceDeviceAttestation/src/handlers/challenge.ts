/**
 * Handler for POST /api/attestation/challenge.
 *
 * Issues a one-time, cryptographically-random challenge hash for a session and
 * binds the caller's clientId + system into the session body. The route parses
 * the query string into {@link AttestationChallengeRequest}; this handler owns
 * all validation and persistence (via the injected {@link ClusterStore}).
 */
import crypto from 'crypto';
import { getSessionData, updateSessionData } from '../server_utils';
import type { ClusterStore } from '../store';
import { jsonResult, fail, type HandlerOutcome, type ErrorBody } from './types';
import { validateIdentity } from './validation';

export const ROUTE = 'attestation/challenge';

/** Query inputs for the challenge endpoint (extracted by the route). */
export interface AttestationChallengeRequest {
  sessionId: string | null;
  clientId: string | null;
  system: string | null;
}

/** Success body: the freshly issued challenge, echoing the bound identity. */
export interface AttestationChallengeSuccess {
  challengeHash: string;
  clientId: string;
  system: string;
}

export type AttestationChallengeResponse = AttestationChallengeSuccess | ErrorBody;

export async function handleAttestationChallenge(
  req: AttestationChallengeRequest,
  store: ClusterStore,
): Promise<HandlerOutcome<AttestationChallengeResponse>> {
  const identity = validateIdentity(req, ROUTE);
  if (!identity.success) return identity.response;
  const { sessionId, clientId, system: systemLower } = identity.data;

  // Check if session exists
  const sessionData = await getSessionData(store, sessionId);
  if (!sessionData) {
    return fail(ROUTE, 404, 'SESSION_NOT_FOUND', 'Session not found', { properties: { sid: sessionId } });
  }

  // Session state is a plain object; the store owns serialization.
  const jsonBody: any = sessionData.data;

  // Check if challenge hash already exists
  if (jsonBody.challengeHash) {
    return fail(ROUTE, 409, 'CHALLENGE_ALREADY_EXISTS', 'Challenge hash already exists for this session', {
      properties: { sid: sessionId },
    });
  }

  // Generate cryptographically secure challenge hash
  const randomBytes = crypto.randomBytes(32);
  const challengeHash = crypto.createHash('sha256').update(randomBytes).digest('hex');

  // Add challengeHash, clientId, and system to jsonBody
  jsonBody.challengeHash = challengeHash;
  jsonBody.clientId = clientId.trim();
  jsonBody.system = systemLower;

  // Update session data
  const updated = await updateSessionData(store, sessionId, sessionData.token, jsonBody, sessionData.version);
  if (!updated) {
    return fail(ROUTE, 500, 'UPDATE_SESSION_FAIL', 'Failed to store challenge', { properties: { sid: sessionId } });
  }

  // Return success response with challenge hash, client ID, and system
  return jsonResult(
    {
      challengeHash: challengeHash,
      clientId: clientId.trim(),
      system: systemLower,
    },
    { status: 200 }
  );
}
