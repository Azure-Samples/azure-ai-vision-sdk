import { validateIdentity, validateInput, validatePayload, signedBodySchema, registrationPayloadSchema } from './validation';
/**
 * Handler for POST /api/attestation/register.
 *
 * Verifies the platform attestation (Play Integrity / App Attest), persists the
 * client's auth certificate, generates the server EC key pair, and stores the
 * exchanged public keys on the session. The route parses the query + JSON body
 * into {@link AttestationRegisterRequest}; this handler owns all validation and
 * persistence (via the injected {@link ClusterStore}).
 */
import { getSessionData, updateSessionData } from '../server_utils';
import { saveCertificate } from '../cert_store';
import {
  validateCertificate,
  computeCertThumbprint,
  extractPublicKeyFromCert,
  validateCertificateExpiration,
} from '../cert_utils';
import {
  generateServerKeyPairEC,
  verifySignatureEC,
} from '../crypto_utils';
import { verifyAuthBySystem } from '../auth_verification';
import { trackApiFail } from '../api_telemetry';
import { trackEvent } from '../logging';
import { StorageError, type ClusterStore } from '../store';
import type { AppAttestVerdict } from '../ios';
import type { PlayIntegrityVerdict } from '../android';
import { getAttestationConfig } from '../config';
import { jsonResult, fail, type HandlerOutcome, type ErrorBody } from './types';

export const ROUTE = 'attestation/register';

/** JSON body accepted by the register endpoint. */
export interface AttestationRegisterBody {
  payload?: string;
  authPublicCert?: string;
  signature?: string;
}

/** Query + parsed body inputs for the register endpoint (built by the route). */
export interface AttestationRegisterRequest {
  sessionId: string | null;
  clientId: string | null;
  system: string | null;
  /** Parsed JSON body, or null when the body was absent / not valid JSON. */
  body: AttestationRegisterBody | null;
}

/** Success body: acknowledgement + the server's encryption public key. */
export interface AttestationRegisterSuccess {
  message: string;
  serverEncryptionPublicKey: string;
}

export type AttestationRegisterResponse = AttestationRegisterSuccess | ErrorBody;

/**
 * Host-facing attestation details returned alongside a successful register, as
 * `outcome.data` (separate from the client `body`). Populated per platform so
 * the host can persist / inspect the full verification outcome.
 */
export interface AttestationRegisterData {
  /** 'ios' or 'android'. */
  platform: string;
  /** Non-fatal verification warnings, if any. */
  warnings?: string[];
  /** Android: Key Attestation chain result (verified to the pinned Google root). */
  androidKeyAttestation?: {
    chainLength?: number;
    rootCA?: string;
    leafCertValidityWarning?: string;
  };
  /** Android: decoded Play Integrity verdict. */
  integrityVerdict?: PlayIntegrityVerdict;
  /** iOS: full App Attest verdict (credCert details + the receipt for the fraud-risk exchange). */
  appAttestVerdict?: AppAttestVerdict;
}

export async function handleAttestationRegister(
  req: AttestationRegisterRequest,
  store: ClusterStore,
): Promise<HandlerOutcome<AttestationRegisterResponse, AttestationRegisterData>> {
  const identity = validateIdentity(req, ROUTE);
  if (!identity.success) return identity.response;
  const { sessionId, clientId, system: systemLower } = identity.data;
  const bodyResult = validateInput(signedBodySchema, req.body, ROUTE, { sid: sessionId });
  if (!bodyResult.success) return bodyResult.response;
  const { payload, authPublicCert, signature } = bodyResult.data;
  const payloadResult = validatePayload(registrationPayloadSchema, payload, ROUTE, sessionId,
    'challengeHash, encryptionPublicCert, and attestJson');
  if (!payloadResult.success) return payloadResult.response;
  const { challengeHash, encryptionPublicCert, attestJson } = payloadResult.data;

  // Validate PEM format for both certificates
  if (!authPublicCert.includes('-----BEGIN CERTIFICATE-----') ||
      !encryptionPublicCert.includes('-----BEGIN CERTIFICATE-----')) {
    return fail(ROUTE, 400, 'INVALID_CERT_PEM', 'Invalid certificate format. Expected PEM format', {
      properties: { sid: sessionId },
    });
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

    // Verify session contains challenge data (must call /api/attestation/challenge first)
    if (!jsonBody.challengeHash || !jsonBody.clientId || !jsonBody.system) {
      return fail(ROUTE, 409, 'SESSION_NOT_INITIALIZED', 'Session not initialized. Call /api/attestation/challenge first', {
        properties: { sid: sessionId },
      });
    }

    const storedChallengeHash = jsonBody.challengeHash;

    // Verify that the client's challengeHash matches the stored one
    if (challengeHash !== storedChallengeHash) {
      return fail(ROUTE, 401, 'CHALLENGE_HASH_MISMATCH', 'Invalid challenge hash', { properties: { sid: sessionId } });
    }

    // Verify request clientId and system match session values
    if (clientId.trim() !== jsonBody.clientId || systemLower !== jsonBody.system) {
      return fail(ROUTE, 401, 'CLIENT_OR_SYSTEM_MISMATCH', 'Client ID or system mismatch', {
        properties: {
          sid: sessionId,
          requestClientId: clientId.trim(),
          sessionClientId: jsonBody.clientId,
          requestSystem: systemLower,
          sessionSystem: jsonBody.system,
        },
      });
    }

    // Validate authentication certificate expiration
    const authExpirationInfo = validateCertificateExpiration(authPublicCert);
    if (!authExpirationInfo) {
      return fail(ROUTE, 500, 'AUTH_CERT_EXP_VALIDATION_FAIL', 'Failed to validate authentication certificate expiration', {
        properties: { sid: sessionId },
      });
    }

    if (authExpirationInfo.isExpired) {
      return fail(ROUTE, 403, 'AUTH_CERT_EXPIRED', 'Authentication certificate has expired', {
        properties: { sid: sessionId, expiredAt: authExpirationInfo.notAfter.toISOString() },
        body: { expiredAt: authExpirationInfo.notAfter.toISOString() },
      });
    }

    if (authExpirationInfo.isNotYetValid) {
      return fail(ROUTE, 403, 'AUTH_CERT_NOT_YET_VALID', 'Authentication certificate is not yet valid', {
        properties: { sid: sessionId, validFrom: authExpirationInfo.notBefore.toISOString() },
        body: { validFrom: authExpirationInfo.notBefore.toISOString() },
      });
    }

    // Validate certificate structure
    const certValidation = validateCertificate(authPublicCert);
    if (!certValidation || !certValidation.valid) {
      return fail(ROUTE, 400, 'AUTH_CERT_STRUCTURE_INVALID', 'Invalid authentication certificate', {
        properties: { sid: sessionId },
      });
    }

    // Extract public key from authentication certificate for signature verification
    const authPublicKey = extractPublicKeyFromCert(authPublicCert);
    if (!authPublicKey) {
      return fail(ROUTE, 500, 'AUTH_PUBKEY_EXTRACT_FAIL', 'Failed to extract public key from authentication certificate', {
        properties: { sid: sessionId },
      });
    }

    // Verify signature over the payload string
    // The signature proves the client has the auth private key and binds the encryption cert to this session
    const signatureValid = verifySignatureEC(payload, signature, authPublicKey);

    if (!signatureValid) {
      return fail(ROUTE, 401, 'PAYLOAD_SIGNATURE_INVALID', 'Invalid signature', { properties: { sid: sessionId } });
    }

    trackEvent('AttestationRegister.PayloadValidated', { platform: systemLower });

    // Verify attestation using verifyAuthBySystem (after signature validation)
    const messageData = {
      challengeHash: jsonBody.challengeHash,
      clientId: jsonBody.clientId,
      system: jsonBody.system,
      publicCert: authPublicCert,
    };

    const verificationResult = await verifyAuthBySystem(messageData, attestJson);
    if (!verificationResult.verified) {
      return fail(ROUTE, 401, 'ATTESTATION_VERIFICATION_FAIL', 'Attestation verification failed', {
        properties: {
          sid: sessionId,
          platform: verificationResult.platform,
          verificationMessage: verificationResult.message,
        },
      });
    }

    trackEvent('AttestationRegister.AttestationVerified', { platform: systemLower });

    // Compute thumbprint from authPublicCert
    const thumbprint = computeCertThumbprint(authPublicCert);
    if (!thumbprint) {
      return fail(ROUTE, 500, 'THUMBPRINT_COMPUTE_FAIL', 'Failed to compute certificate thumbprint', {
        properties: { sid: sessionId },
      });
    }

    trackEvent('AttestationRegister.CertificateStoreStarted', { platform: systemLower });

    // Check if already generated (one-time flag)
    if (jsonBody.serverKeyGenerated) {
      return fail(ROUTE, 409, 'SERVER_KEYS_ALREADY_GENERATED', 'Server keys already generated for this session', {
        properties: { sid: sessionId },
      });
    }

    // Validate encryption certificate expiration
    const encryptionExpirationInfo = validateCertificateExpiration(encryptionPublicCert);
    if (!encryptionExpirationInfo) {
      return fail(ROUTE, 500, 'ENC_CERT_EXP_VALIDATION_FAIL', 'Failed to validate encryption certificate expiration', {
        properties: { sid: sessionId },
      });
    }

    if (encryptionExpirationInfo.isExpired) {
      return fail(ROUTE, 403, 'ENC_CERT_EXPIRED', 'Encryption certificate has expired', {
        properties: { sid: sessionId, expiredAt: encryptionExpirationInfo.notAfter.toISOString() },
        body: { expiredAt: encryptionExpirationInfo.notAfter.toISOString() },
      });
    }

    if (encryptionExpirationInfo.isNotYetValid) {
      return fail(ROUTE, 403, 'ENC_CERT_NOT_YET_VALID', 'Encryption certificate is not yet valid', {
        properties: { sid: sessionId, validFrom: encryptionExpirationInfo.notBefore.toISOString() },
        body: { validFrom: encryptionExpirationInfo.notBefore.toISOString() },
      });
    }

    // Extract and validate encryption public key (ensures P-256 curve)
    const clientEncryptionPublicKey = extractPublicKeyFromCert(encryptionPublicCert);
    if (!clientEncryptionPublicKey) {
      return fail(ROUTE, 500, 'ENC_PUBKEY_EXTRACT_FAIL', 'Failed to extract public key from encryption certificate', {
        properties: { sid: sessionId },
      });
    }

    trackEvent('AttestationRegister.EncryptionCertificateValidated', { platform: systemLower });

    // Generate server EC key pair
    const keyPair = generateServerKeyPairEC();
    if (!keyPair) {
      return fail(ROUTE, 500, 'SERVER_KEYPAIR_GEN_FAIL', 'Failed to generate server key pair', {
        properties: { sid: sessionId },
      });
    }

    const saveResult = await saveCertificate(store, clientId.trim(), systemLower as 'ios' | 'android', authPublicCert, {
      verificationTimestamp: verificationResult.timestamp,
      attestJson,
      integrityVerdict: verificationResult.integrityVerdict,
      appAttestVerdict: verificationResult.appAttestVerdict,
    });
    if (!saveResult) {
      return fail(ROUTE, 409, 'SAVE_CERT_FAIL', 'Certificate changed or could not be created');
    }

    // Update session with keys and cert info
    jsonBody.serverKeyGenerated = true;
    jsonBody.serverEncryptionPrivateKey = keyPair.privateKey;
    jsonBody.serverEncryptionPublicKey = keyPair.publicKey;
    jsonBody.clientAuthPublicKey = authPublicKey;  // Authentication public key for signature verification
    jsonBody.clientEncryptionPublicKey = clientEncryptionPublicKey;  // Encryption public key for response encryption
    jsonBody.certRegistered = true;
    jsonBody.clientAuthCertThumbprint = saveResult.thumbprint;  // Authentication cert thumbprint

    const updated = await updateSessionData(store, sessionId, sessionData.token, jsonBody, sessionData.version);
    if (!updated) {
      return fail(ROUTE, 500, 'UPDATE_SESSION_FAIL', 'Failed to update session with server keys', {
        properties: { sid: sessionId },
      });
    }

    trackEvent('AttestationRegister.ServerKeysGenerated', { platform: systemLower });

    // Return success + the server's public key in the client `body`. Expose the
    // full attestation details as host-facing `data` (separate from `body`):
    // Android = Key Attestation chain + Play Integrity verdict; iOS = the App
    // Attest verdict incl. the receipt for a later fraud-risk (DeviceCheck)
    // exchange. Mirrors how handleLivenessDigest exposes `clientDigest`.
    const data: AttestationRegisterData =
      systemLower === 'ios'
        ? {
            platform: verificationResult.platform,
            appAttestVerdict: verificationResult.appAttestVerdict,
            ...(verificationResult.warnings && { warnings: verificationResult.warnings }),
          }
        : {
            platform: verificationResult.platform,
            androidKeyAttestation: {
              chainLength: verificationResult.chainLength,
              rootCA: verificationResult.rootCA,
              ...(verificationResult.leafCertValidityWarning && {
                leafCertValidityWarning: verificationResult.leafCertValidityWarning,
              }),
            },
            integrityVerdict: verificationResult.integrityVerdict,
            ...(verificationResult.warnings && { warnings: verificationResult.warnings }),
          };

    return jsonResult(
      {
        message: 'Certificate stored successfully',
        serverEncryptionPublicKey: keyPair.publicKey,
      },
      { status: 200, data }
    );
  } catch (error) {
    if (error instanceof StorageError) throw error;
    trackApiFail(ROUTE, 'INTERNAL_ERROR', 500, {
      sid: sessionId,
      errorMessage: error instanceof Error ? error.message : String(error),
    });
    // Re-throw so the wrapper records the exception with stack trace
    throw error;
  }
}
