/**
 * Handler for POST /api/liveness/digest.
 *
 * Accepts the client's signed + encrypted liveness digest at the end of the
 * flow: verifies the auth-cert signature (and, on iOS, a fresh App Attest
 * assertion), decrypts the payload, checks clientId/os against the session,
 * records the digest, and returns an encrypted acknowledgement. The route
 * parses the query + JSON body into {@link LivenessDigestRequest}.
 */
import { getSessionData, updateSessionData } from '../server_utils';
import {
  decryptWithPrivateKeyEC,
  verifySignatureEC,
  encryptWithPublicKeyEC,
} from '../crypto_utils';
import { checkIosAssertionForRoute } from '../ios_assertion_check';
import { trackApiFail } from '../api_telemetry';
import { trackException } from '../logging';
import type { ClusterStore } from '../store';
import { jsonResult, fail, type HandlerOutcome, type ErrorBody } from './types';
import { encryptedBodySchema, sessionIdSchema, validateInput } from './validation';

export const ROUTE = 'liveness/digest';

/**
 * JSON body accepted by the digest endpoint. `assertion` is required when
 * session.system === 'ios'; it signs the UTF-8 bytes of encryptedData.
 */
export interface LivenessDigestBody {
  encryptedData?: string;
  signature?: string;
  assertion?: string;
}

/** Query + parsed body inputs for the digest endpoint (built by the route). */
export interface LivenessDigestRequest {
  sessionId: string | null;
  /** Parsed JSON body, or null when the body was absent / not valid JSON. */
  body: LivenessDigestBody | null;
}

/** Success body: the Tink ECIES blob (base64) carrying the encrypted ack. */
export interface LivenessDigestSuccess {
  encryptedData: string;
}

export type LivenessDigestResponse = LivenessDigestSuccess | ErrorBody;

export async function handleLivenessDigest(
  req: LivenessDigestRequest,
  store: ClusterStore,
): Promise<HandlerOutcome<LivenessDigestResponse, { clientDigest: string }>> {
  const sessionResult = validateInput(sessionIdSchema, req.sessionId, ROUTE);
  if (!sessionResult.success) return sessionResult.response;
  const sessionId = sessionResult.data;
  const bodyResult = validateInput(encryptedBodySchema, req.body, ROUTE, { sid: sessionId });
  if (!bodyResult.success) return bodyResult.response;
  const requestBody = bodyResult.data;

  // Retrieve session data
  const sessionData = await getSessionData(store, sessionId);
  if (!sessionData) {
    return fail(ROUTE, 404, 'SESSION_NOT_FOUND', 'Session not found', { properties: { sid: sessionId } });
  }

  // Session state is a plain object; the store owns serialization.
  const storedData: any = sessionData.data;

  // Verify serverKeyGenerated flag is true
  if (!storedData.serverKeyGenerated) {
    return fail(
      ROUTE,
      409,
      'SERVER_KEYS_NOT_GENERATED',
      'Server keys not generated. Call /api/attestation/verify or /api/attestation/register first',
      { properties: { sid: sessionId } },
    );
  }

  // Verify certRegistered flag is true
  if (!storedData.certRegistered) {
    return fail(
      ROUTE,
      409,
      'CERT_NOT_REGISTERED',
      'Certificate not registered. Call /api/attestation/verify or /api/attestation/register first',
      { properties: { sid: sessionId } },
    );
  }

  // Check if digest already exists (one-time check)
  if (!storedData.authCompleted) {
    return fail(ROUTE, 409, 'AUTH_NOT_COMPLETED', 'Obtain the session token before submitting a digest');
  }
  if (storedData.digestCompleted) {
    return fail(ROUTE, 409, 'DIGEST_ALREADY_EXISTS', 'Digest already exists for this session', {
      properties: { sid: sessionId },
    });
  }

  // Verify signature using client's auth public key
  const clientAuthPublicKey = storedData.clientAuthPublicKey;
  if (!clientAuthPublicKey) {
    return fail(ROUTE, 500, 'MISSING_CLIENT_AUTH_PUBKEY', 'Client auth public key not found in session', {
      properties: { sid: sessionId },
    });
  }

  // Verify signature of the Tink ciphertext (base64 string)
  const isSignatureValid = verifySignatureEC(
    requestBody.encryptedData,
    requestBody.signature,
    clientAuthPublicKey
  );

  if (!isSignatureValid) {
    return fail(ROUTE, 401, 'SIGNATURE_INVALID', 'Signature verification failed', { properties: { sid: sessionId } });
  }

  // iOS: verify a fresh App Attest assertion against the credCert public key
  // persisted at registration. signCount monotonicity prevents replays.
  let commitAssertion: (() => Promise<boolean>) | undefined;
  if (storedData.system === 'ios') {
    const thumbprint = storedData.clientAuthCertThumbprint;
    if (!thumbprint || typeof thumbprint !== 'string') {
      return fail(ROUTE, 500, 'MISSING_CERT_THUMBPRINT', 'Client cert thumbprint not found in session', {
        properties: { sid: sessionId },
      });
    }

    const r = await checkIosAssertionForRoute(store, {
      routeName: ROUTE,
      sessionId,
      thumbprint,
      blob: Buffer.from(requestBody.encryptedData, 'utf8'),
      assertion: req.body!.assertion,
    });
    if (!r.ok) return r.result;
    commitAssertion = r.commit;
  }

  // Decrypt encryptedData using server's encryption private key
  const serverEncryptionPrivateKey = storedData.serverEncryptionPrivateKey;
  if (!serverEncryptionPrivateKey) {
    return fail(ROUTE, 500, 'MISSING_SERVER_ENC_PRIVKEY', 'Server encryption private key not found in session', {
      properties: { sid: sessionId },
    });
  }

  const decryptedPayload = decryptWithPrivateKeyEC(requestBody.encryptedData, serverEncryptionPrivateKey);
  if (!decryptedPayload) {
    return fail(ROUTE, 401, 'DECRYPTION_FAIL', 'Decryption failed', { properties: { sid: sessionId } });
  }

  // Parse decrypted payload as JSON
  let messageData: { cid?: string; os?: string; digest?: string };
  try {
    messageData = JSON.parse(decryptedPayload);
  } catch (error) {
    trackException(error, { source: 'handleLivenessDigest.parsePayload', sessionId });
    return fail(ROUTE, 401, 'DECRYPTED_PAYLOAD_PARSE_ERROR', 'Invalid decrypted payload format', {
      properties: { sid: sessionId },
    });
  }

  // Validate required fields
  if (!messageData.cid || typeof messageData.cid !== 'string' || messageData.cid.trim() === '') {
    return fail(ROUTE, 400, 'MISSING_CID', 'Missing or invalid "cid" in payload', { properties: { sid: sessionId } });
  }

  // Extract and validate fields
  const clientId = messageData.cid.trim();
  const os = messageData.os || '';
  const digest = messageData.digest || '';
  if (typeof digest !== 'string' || !digest.trim()) {
    return fail(ROUTE, 400, 'INVALID_DIGEST', 'Expected a nonempty digest');
  }

  // Verify client ID matches (if clientId exists in jsonBody from prior auth calls)
  if (storedData.clientId && storedData.clientId !== clientId) {
    return fail(ROUTE, 401, 'CLIENT_ID_MISMATCH', 'Client ID mismatch', {
      properties: {
        sid: sessionId,
        expectedClientId: storedData.clientId,
        actualClientId: clientId,
      },
    });
  }

  // Verify OS matches (if system exists in jsonBody from prior auth calls)
  if (storedData.system && storedData.system !== os) {
    return fail(ROUTE, 401, 'OS_MISMATCH', 'Operating system mismatch', {
      properties: {
        sid: sessionId,
        expectedSystem: storedData.system,
        actualOs: os,
      },
    });
  }

  // Store digest data in session
  storedData.digest = digest;
  storedData.attestationClientId = clientId;
  storedData.attestationOs = os;
  storedData.digestCompleted = true; // One-time flag

  // Prepare response payload
  const responsePayload = {
    success: true,
    timestamp: new Date().toISOString(),
  };

  const responsePayloadString = JSON.stringify(responsePayload);

  // Get client's encryption public key for encrypting response
  const clientEncryptionPublicKey = storedData.clientEncryptionPublicKey;
  if (!clientEncryptionPublicKey) {
    return fail(ROUTE, 500, 'MISSING_CLIENT_ENC_PUBKEY', 'Client encryption public key not found in session', {
      properties: { sid: sessionId },
    });
  }

  // Encrypt response with client's encryption public key (returns Tink format base64 string)
  const encryptedResponse = encryptWithPublicKeyEC(responsePayloadString, clientEncryptionPublicKey);
  if (!encryptedResponse) {
    return fail(ROUTE, 500, 'RESPONSE_ENCRYPT_FAIL', 'Failed to encrypt response', { properties: { sid: sessionId } });
  }

  if (commitAssertion && !await commitAssertion()) {
    return fail(ROUTE, 409, 'ASSERTION_STATE_CONFLICT', 'Certificate changed or expired; obtain a fresh assertion');
  }
  if (!await updateSessionData(store, sessionId, sessionData.token, storedData, sessionData.version)) {
    return fail(ROUTE, 409, 'SESSION_STATE_CONFLICT', 'Session changed or expired');
  }

  // Return encrypted response (no signature, consistent with verify and register).
  // Expose the client-submitted digest as host-facing `data` (separate from the
  // encrypted client `body`) so the host can persist/compare it later without
  // reaching into the library's session record.
  return jsonResult(
    { encryptedData: encryptedResponse },
    { status: 200, data: { clientDigest: digest } }
  );
}
