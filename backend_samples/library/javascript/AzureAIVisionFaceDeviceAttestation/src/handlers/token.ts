/**
 * Handler for POST /api/session/token.
 *
 * Completes authentication: verifies the auth-cert signature (and, on iOS, a
 * fresh App Attest assertion) over the encrypted request, decrypts it, checks
 * the enclosed challenge/clientId/system against the session, marks the session
 * authenticated, and returns the Face session token encrypted to the client.
 * The route parses the query + JSON body into {@link SessionTokenRequest}.
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

export const ROUTE = 'session/token';

/**
 * JSON body accepted by the token endpoint. `assertion` is required when
 * session.system === 'ios' (base64 CBOR from DCAppAttestService.generateAssertion);
 * it signs the UTF-8 bytes of encryptedData.
 */
export interface SessionTokenBody {
  encryptedData?: string;
  signature?: string;
  assertion?: string;
}

/** Query + parsed body inputs for the token endpoint (built by the route). */
export interface SessionTokenRequest {
  sessionId: string | null;
  /** Parsed JSON body, or null when the body was absent / not valid JSON. */
  body: SessionTokenBody | null;
}

/** Success body: the Tink ECIES blob (base64) carrying the encrypted response. */
export interface SessionTokenSuccess {
  encryptedData: string;
}

export type SessionTokenResponse = SessionTokenSuccess | ErrorBody;

export async function handleSessionToken(
  req: SessionTokenRequest,
  store: ClusterStore,
): Promise<HandlerOutcome<SessionTokenResponse>> {
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
    return fail(ROUTE, 409, 'SERVER_KEYS_NOT_GENERATED', 'Server keys not generated. Call /api/attestation/verify or /api/attestation/register first', {
      properties: { sid: sessionId },
    });
  }

  // Verify certRegistered flag is true
  if (!storedData.certRegistered) {
    return fail(ROUTE, 409, 'CERT_NOT_REGISTERED', 'Certificate not registered. Call /api/attestation/verify or /api/attestation/register first', {
      properties: { sid: sessionId },
    });
  }

  // Check if authCompleted flag exists (one-time check)
  if (storedData.authCompleted) {
    return fail(ROUTE, 409, 'AUTH_ALREADY_COMPLETED', 'Authentication already completed for this session', {
      properties: { sid: sessionId },
    });
  }

  // Verify signature using clientAuthPublicKey
  const clientAuthPublicKey = storedData.clientAuthPublicKey;
  if (!clientAuthPublicKey) {
    return fail(ROUTE, 500, 'MISSING_CLIENT_AUTH_PUBKEY', 'Client authentication public key not found in session', {
      properties: { sid: sessionId },
    });
  }

  // Get client encryption public key for response encryption
  const clientEncryptionPublicKey = storedData.clientEncryptionPublicKey;
  if (!clientEncryptionPublicKey) {
    return fail(ROUTE, 500, 'MISSING_CLIENT_ENC_PUBKEY', 'Client encryption public key not found in session', {
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

  // Decrypt encryptedData using serverEncryptionPrivateKey
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
  let messageData: { challengeHash?: string; clientId?: string; system?: string };
  try {
    messageData = JSON.parse(decryptedPayload);
  } catch (error) {
    trackException(error, { source: 'handleSessionToken.parsePayload', sessionId });
    return fail(ROUTE, 401, 'DECRYPTED_PAYLOAD_PARSE_ERROR', 'Invalid decrypted payload format', {
      properties: { sid: sessionId },
    });
  }

  // Verify payload matches stored data
  if (
    !messageData.challengeHash ||
    !messageData.clientId ||
    !messageData.system ||
    messageData.challengeHash !== storedData.challengeHash ||
    messageData.clientId !== storedData.clientId ||
    messageData.system !== storedData.system
  ) {
    return fail(ROUTE, 401, 'PAYLOAD_MISMATCH', 'Payload mismatch', {
      properties: {
        sid: sessionId,
        hasChallenge: !!messageData.challengeHash,
        hasClientId: !!messageData.clientId,
        hasSystem: !!messageData.system,
      },
    });
  }

  // Mark authCompleted = true in session (one-time flag)
  storedData.authCompleted = true;

  // Prepare response payload
  const responsePayload = {
    token: sessionData.token,
    timestamp: new Date().toISOString(),
  };

  const responsePayloadString = JSON.stringify(responsePayload);

  // Encrypt response with clientEncryptionPublicKey (returns Tink format base64 string)
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

  // Return only encrypted data (no signature). The encryption already provides
  // confidentiality and the client can decrypt with their private key.
  return jsonResult(
    {
      encryptedData: encryptedResponse, // Base64-encoded Tink ECIES blob
    },
    { status: 200 }
  );
}
