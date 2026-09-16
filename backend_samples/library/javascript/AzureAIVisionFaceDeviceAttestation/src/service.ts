/**
 * The attestation library's public surface.
 *
 * `createAttestationService(config, store, logger)` configures the library and returns
 * an {@link AttestationService} whose methods implement every attestation route
 * plus the /.well-known responses. The host builds it once (a singleton) and
 * routes call its methods — so the routes never wire up storage or config and
 * the library never touches `process.env`.
 */
import type { ClusterStore } from './store';
import { StorageError } from './store';
import { fail, type HandlerOutcome, type ErrorBody } from './handlers/types';
import { configureAttestation } from './config';
import { handleAttestationChallenge, type AttestationChallengeRequest } from './handlers/challenge';
import { handleAttestationRegister, type AttestationRegisterRequest } from './handlers/register';
import { handleAttestationVerify, type AttestationVerifyRequest } from './handlers/verify';
import { handleSessionToken, type SessionTokenRequest } from './handlers/token';
import { handleLivenessDigest, type LivenessDigestRequest } from './handlers/digest';
import { appleAppSiteAssociation, assetlinks } from './well_known';
import { setAttestationLogger, type AttestationLogger } from './logging';
import { getSessionData, saveToken } from './server_utils';

/**
 * Handles every attestation endpoint + /.well-known document. Holds the
 * persistent-storage backend; the runtime config is installed by the factory.
 */
export class AttestationService {
  constructor(private readonly store: ClusterStore) {}

  /** POST /api/attestation/challenge */
  challenge(req: AttestationChallengeRequest) {
    return this.withStorageFailure(() => handleAttestationChallenge(req, this.store));
  }

  /** POST /api/attestation/register */
  register(req: AttestationRegisterRequest) {
    return this.withStorageFailure(() => handleAttestationRegister(req, this.store));
  }

  /** POST /api/attestation/verify */
  verify(req: AttestationVerifyRequest) {
    return this.withStorageFailure(() => handleAttestationVerify(req, this.store));
  }

  /** POST /api/session/token */
  sessionToken(req: SessionTokenRequest) {
    return this.withStorageFailure(() => handleSessionToken(req, this.store));
  }

  /** POST /api/liveness/digest */
  livenessDigest(req: LivenessDigestRequest) {
    return this.withStorageFailure(() => handleLivenessDigest(req, this.store));
  }

  private async withStorageFailure<TBody, TData>(action: () => Promise<HandlerOutcome<TBody, TData>>): Promise<HandlerOutcome<TBody | ErrorBody, TData>> {
    try {
      return await action();
    } catch (error) {
      if (!(error instanceof StorageError)) throw error;
      return fail('attestation', 503, 'STORAGE_UNAVAILABLE', 'Attestation storage unavailable');
    }
  }

  /** Whether a session record exists (created by {@link saveSession}), by id. */
  async sessionExists(sid: string): Promise<boolean> {
    return (await getSessionData(this.store, sid)) !== null;
  }

  /**
   * Liveness-completion signal for a session: whether the client has posted its
   * digest yet and, if so, the digest it submitted. Lets the host poll for
   * completion and compare the digest WITHOUT reading the library's internal
   * session-record shape.
   */
  async getLivenessOutcome(sid: string): Promise<{ completed: boolean; clientDigest?: string }> {
    const session = await getSessionData(this.store, sid);
    if (!session || !session.data.digestCompleted) {
      return { completed: false };
    }
    const rawDigest = session.data.digest;
    return {
      completed: true,
      clientDigest: typeof rawDigest === 'string' ? rawDigest : undefined,
    };
  }

  /** Seed a new session with the Face token; attestation state starts empty. */
  saveSession(sid: string, token: string) {
    return saveToken(this.store, sid, token);
  }

  /** GET /.well-known/apple-app-site-association */
  appleAppSiteAssociation() {
    return appleAppSiteAssociation();
  }

  /** GET /.well-known/assetlinks.json */
  androidAssetLinks() {
    return assetlinks();
  }
}

/**
 * Configure the library and construct the service. Call once at startup; the
 * host should cache the result as a singleton.
 */
export function createAttestationService(
  config: import('./config').AttestationConfig,
  store: ClusterStore,
  logger: AttestationLogger,
): AttestationService {
  configureAttestation(config);
  setAttestationLogger(logger);
  return new AttestationService(store);
}
