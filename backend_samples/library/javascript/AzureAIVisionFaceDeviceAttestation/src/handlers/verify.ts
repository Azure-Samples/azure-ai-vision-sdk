/**
 * Handler for POST /api/attestation/verify.
 *
 * For an already-registered client, re-checks the auth-cert signature (and, on
 * iOS, a fresh App Attest assertion), then issues the server encryption key and
 * marks the session ready. Returns `{ exists: false }` when the cert has not yet
 * been registered (the client should then call /api/attestation/register). The
 * route parses the query + JSON body into {@link AttestationVerifyRequest}; this
 * handler owns validation and persistence (via the injected {@link ClusterStore}).
 */
import { getSessionData, updateSessionData } from '../server_utils';
import { getCertificate } from '../cert_store';
import {
  extractPublicKeyFromCert,
  validateCertificateExpiration,
  computeCertThumbprint,
} from '../cert_utils';
import {
  generateServerKeyPairEC,
  verifySignatureEC,
} from '../crypto_utils';
import { checkIosAssertionForRoute } from '../ios_assertion_check';
import { trackApiFail } from '../api_telemetry';
import { trackEvent, trackException } from '../logging';
import { StorageError, type ClusterStore } from '../store';
import { getAttestationConfig } from '../config';
import { jsonResult, fail, type HandlerOutcome, type ErrorBody } from './types';
import { validateIdentity, validateInput, validatePayload, signedBodySchema, verificationPayloadSchema } from './validation';

export const ROUTE = 'attestation/verify';

/** JSON body accepted by the verify endpoint. */
export interface AttestationVerifyBody {
  payload?: string;
  authPublicCert?: string;
  signature?: string;
  // `assertion` is a top-level base64 CBOR (DCAppAttestService.generateAssertion result).
  // Required when system==='ios' and the cert has already been registered with App Attest.
  assertion?: string;
}

/** Query + parsed body inputs for the verify endpoint (built by the route). */
export interface AttestationVerifyRequest {
  sessionId: string | null;
  clientId: string | null;
  system: string | null;
  /** Parsed JSON body, or null when the body was absent / not valid JSON. */
  body: AttestationVerifyBody | null;
}

/** Success body: whether the cert exists and, if so, the server's enc public key. */
export interface AttestationVerifyResult {
  exists: boolean;
  serverEncryptionPublicKey?: string;
}

export type AttestationVerifyResponse = AttestationVerifyResult | ErrorBody;

export async function handleAttestationVerify(
  req: AttestationVerifyRequest,
  store: ClusterStore,
): Promise<HandlerOutcome<AttestationVerifyResponse>> {
  const identity = validateIdentity(req, ROUTE);
  if (!identity.success) return identity.response;
  const { sessionId, clientId, system: systemLower } = identity.data;
  const bodyResult = validateInput(signedBodySchema, req.body, ROUTE, { sid: sessionId });
  if (!bodyResult.success) return bodyResult.response;
  const { payload, authPublicCert, signature } = bodyResult.data;
  const assertion = req.body!.assertion;
  const payloadResult = validatePayload(verificationPayloadSchema, payload, ROUTE, sessionId);
  if (!payloadResult.success) return payloadResult.response;
  const { challengeHash, encryptionPublicCert } = payloadResult.data;

  // Validate PEM format for both certificates
  if (!authPublicCert.includes('-----BEGIN CERTIFICATE-----') ||
      !encryptionPublicCert.includes('-----BEGIN CERTIFICATE-----')) {
    return fail(ROUTE, 400, 'INVALID_CERT_PEM', 'Invalid certificate format. Expected PEM format', { properties: { sid: sessionId } });
  }

  // Validate certificate size (prevent DOS with huge certs)
  const maxCertSize = getAttestationConfig().maxCertSize;
  if (authPublicCert.length > maxCertSize || encryptionPublicCert.length > maxCertSize) {
    return fail(ROUTE, 400, 'CERT_TOO_LARGE', `Certificate size exceeds maximum allowed size of ${maxCertSize} bytes`, {
      properties: {
        sid: sessionId,
        maxCertSize,
        authBytes: authPublicCert.length,
        encryptionBytes: encryptionPublicCert.length,
      },
    });
  }

  try {
    // Get session data to retrieve stored challenge hash
    const sessionData = await getSessionData(store, sessionId);
    if (!sessionData) {
      return fail(ROUTE, 404, 'SESSION_NOT_FOUND', 'Session not found', { properties: { sid: sessionId } });
    }

    // Session state is a plain object; the store owns serialization.
    const jsonBody: any = sessionData.data;

    const storedChallengeHash = jsonBody.challengeHash;
    if (!storedChallengeHash) {
      return fail(ROUTE, 400, 'CHALLENGE_NOT_INITIALIZED', 'Challenge hash not found in session. Session may have expired or not been initialized.', { properties: { sid: sessionId } });
    }

    // Verify that the client's challengeHash matches the stored one
    if (challengeHash !== storedChallengeHash) {
      return fail(ROUTE, 401, 'CHALLENGE_HASH_MISMATCH', 'Invalid challenge hash', { properties: { sid: sessionId } });
    }

    // Verify clientId + system match what was stored at attestation/challenge.
    const storedClientId = jsonBody.clientId;
    if (storedClientId && storedClientId !== clientId.trim()) {
      return fail(ROUTE, 401, 'CLIENT_ID_MISMATCH', 'Client ID mismatch', {
        properties: {
          sid: sessionId,
          expectedClientId: storedClientId,
          actualClientId: clientId.trim(),
        },
      });
    }
    const storedSystem = jsonBody.system;
    if (storedSystem && storedSystem !== systemLower) {
      return fail(ROUTE, 401, 'SYSTEM_MISMATCH', 'System mismatch', {
        properties: {
          sid: sessionId,
          expectedSystem: storedSystem,
          actualSystem: systemLower,
        },
      });
    }

    // Validate authentication certificate expiration
    const authExpirationInfo = validateCertificateExpiration(authPublicCert);
    if (!authExpirationInfo) {
      return fail(ROUTE, 500, 'AUTH_CERT_EXP_VALIDATION_FAIL', 'Failed to validate authentication certificate expiration', { properties: { sid: sessionId } });
    }

    if (authExpirationInfo.isExpired) {
      return fail(ROUTE, 403, 'AUTH_CERT_EXPIRED', 'Authentication certificate has expired', {
        properties: {
          sid: sessionId,
          expiredAt: authExpirationInfo.notAfter.toISOString(),
        },
        body: { expiredAt: authExpirationInfo.notAfter.toISOString() },
      });
    }

    if (authExpirationInfo.isNotYetValid) {
      return fail(ROUTE, 403, 'AUTH_CERT_NOT_YET_VALID', 'Authentication certificate is not yet valid', {
        properties: {
          sid: sessionId,
          validFrom: authExpirationInfo.notBefore.toISOString(),
        },
        body: { validFrom: authExpirationInfo.notBefore.toISOString() },
      });
    }

    // Extract public key from authentication certificate for signature verification
    const authPublicKey = extractPublicKeyFromCert(authPublicCert);
    if (!authPublicKey) {
      return fail(ROUTE, 500, 'AUTH_PUBKEY_EXTRACT_FAIL', 'Failed to extract public key from authentication certificate', { properties: { sid: sessionId } });
    }

    // Verify signature over the payload string. The signature proves the client
    // holds the auth private key and binds the encryption cert to this session.
    const signatureValid = verifySignatureEC(payload, signature, authPublicKey);

    if (!signatureValid) {
      return fail(ROUTE, 401, 'PAYLOAD_SIGNATURE_INVALID', 'Invalid signature', { properties: { sid: sessionId } });
    }

    trackEvent('AttestationVerify.PayloadValidated', {
      sessionId,
      clientId: clientId.trim(),
      platform: systemLower,
    });

    // Compute thumbprint from authPublicCert (keeps existing cert-store schema)
    const thumbprint = computeCertThumbprint(authPublicCert);
    if (!thumbprint) {
      return fail(ROUTE, 500, 'THUMBPRINT_COMPUTE_FAIL', 'Failed to compute certificate thumbprint', { properties: { sid: sessionId } });
    }

    trackEvent('AttestationVerify.CertificateLookupStarted', {
      sessionId,
      clientId: clientId.trim(),
      platform: systemLower,
      thumbprint,
    });

    // Load the cert record so we can both check existence AND verify it was
    // registered for *this* clientId + system.
    const certSnapshot = await getCertificate(store, thumbprint);
    const certRecord = certSnapshot?.value;

    if (!certRecord) {
      // Certificate not found - client must register first via /api/attestation/register
      trackApiFail(ROUTE, 'CERT_NOT_REGISTERED', 200, {
        sid: sessionId,
        thumbprint,
        clientId: clientId.trim(),
        system: systemLower,
      });
      return jsonResult({ exists: false }, { status: 200 });
    }

    if (certRecord.clientId !== clientId.trim()) {
      return fail(ROUTE, 401, 'CERT_CLIENT_ID_MISMATCH', 'Certificate clientId mismatch', {
        properties: {
          sid: sessionId,
          thumbprint,
          expectedClientId: certRecord.clientId,
          actualClientId: clientId.trim(),
        },
      });
    }
    if (certRecord.system !== systemLower) {
      return fail(ROUTE, 401, 'CERT_SYSTEM_MISMATCH', 'Certificate system mismatch', {
        properties: {
          sid: sessionId,
          thumbprint,
          expectedSystem: certRecord.system,
          actualSystem: systemLower,
        },
      });
    }

    // iOS: re-exercise Apple hardware on this call by verifying a fresh App Attest
    // assertion against the credCert public key we persisted at registration.
    let commitAssertion: (() => Promise<boolean>) | undefined;
    if (systemLower === 'ios') {
      const r = await checkIosAssertionForRoute(store, {
        routeName: ROUTE,
        sessionId,
        thumbprint,
        blob: Buffer.from(payload, 'utf8'),
        assertion,
      });
      if (!r.ok) return r.result;
      commitAssertion = r.commit;
    }

    // Check if already generated (one-time flag)
    if (jsonBody.serverKeyGenerated) {
      return fail(ROUTE, 409, 'SERVER_KEYS_ALREADY_GENERATED', 'Server keys already generated for this session', { properties: { sid: sessionId } });
    }

    // Validate encryption certificate expiration
    const encryptionExpirationInfo = validateCertificateExpiration(encryptionPublicCert);
    if (!encryptionExpirationInfo) {
      return fail(ROUTE, 500, 'ENC_CERT_EXP_VALIDATION_FAIL', 'Failed to validate encryption certificate expiration', { properties: { sid: sessionId } });
    }

    if (encryptionExpirationInfo.isExpired) {
      return fail(ROUTE, 403, 'ENC_CERT_EXPIRED', 'Encryption certificate has expired', {
        properties: {
          sid: sessionId,
          expiredAt: encryptionExpirationInfo.notAfter.toISOString(),
        },
        body: { expiredAt: encryptionExpirationInfo.notAfter.toISOString() },
      });
    }

    if (encryptionExpirationInfo.isNotYetValid) {
      return fail(ROUTE, 403, 'ENC_CERT_NOT_YET_VALID', 'Encryption certificate is not yet valid', {
        properties: {
          sid: sessionId,
          validFrom: encryptionExpirationInfo.notBefore.toISOString(),
        },
        body: { validFrom: encryptionExpirationInfo.notBefore.toISOString() },
      });
    }

    // Extract and validate encryption public key (ensures P-256 curve)
    const clientEncryptionPublicKey = extractPublicKeyFromCert(encryptionPublicCert);
    if (!clientEncryptionPublicKey) {
      return fail(ROUTE, 500, 'ENC_PUBKEY_EXTRACT_FAIL', 'Failed to extract public key from encryption certificate', { properties: { sid: sessionId } });
    }

    trackEvent('AttestationVerify.EncryptionCertificateValidated', {
      sessionId,
      platform: systemLower,
    });

    // Generate server EC key pair
    const keyPair = generateServerKeyPairEC();
    if (!keyPair) {
      return fail(ROUTE, 500, 'SERVER_KEYPAIR_GEN_FAIL', 'Failed to generate server key pair', { properties: { sid: sessionId } });
    }

    // Update session with keys and cert info
    jsonBody.serverKeyGenerated = true;
    jsonBody.serverEncryptionPrivateKey = keyPair.privateKey;
    jsonBody.serverEncryptionPublicKey = keyPair.publicKey;
    jsonBody.clientAuthPublicKey = authPublicKey;  // Authentication public key for signature verification
    jsonBody.clientEncryptionPublicKey = clientEncryptionPublicKey;  // Encryption public key for response encryption
    jsonBody.certRegistered = true;
    jsonBody.clientAuthCertThumbprint = thumbprint;  // Authentication cert thumbprint

    if (commitAssertion && !await commitAssertion()) {
      return fail(ROUTE, 409, 'ASSERTION_STATE_CONFLICT', 'Certificate changed or expired; obtain a fresh assertion');
    }
    const updated = await updateSessionData(store, sessionId, sessionData.token, jsonBody, sessionData.version);
    if (!updated) {
      return fail(ROUTE, 500, 'UPDATE_SESSION_FAIL', 'Failed to update session with server keys', { properties: { sid: sessionId } });
    }

    trackEvent('AttestationVerify.ServerKeysGenerated', {
      sessionId,
      platform: systemLower,
    });

    // Return success with the server's encryption public key
    return jsonResult(
      {
        exists: true,
        serverEncryptionPublicKey: keyPair.publicKey,
      },
      { status: 200 }
    );
  } catch (error) {
    trackException(error, { source: 'handleAttestationVerify', sessionId });
    if (error instanceof StorageError) throw error;
    return fail(ROUTE, 500, 'STORAGE_ERROR', 'Storage operation failed', {
      properties: {
        sid: sessionId,
        errorMessage: error instanceof Error ? error.message : String(error),
      },
    });
  }
}
