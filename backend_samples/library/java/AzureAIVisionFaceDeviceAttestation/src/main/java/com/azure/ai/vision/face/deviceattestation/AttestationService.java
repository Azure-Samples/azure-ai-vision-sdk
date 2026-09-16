package com.azure.ai.vision.face.deviceattestation;

import java.util.List;
import java.util.Map;

import com.azure.ai.vision.face.deviceattestation.config.AttestationConfig;
import com.azure.ai.vision.face.deviceattestation.handlers.ChallengeHandler;
import com.azure.ai.vision.face.deviceattestation.handlers.DigestHandler;
import com.azure.ai.vision.face.deviceattestation.handlers.HandlerOutcome;
import com.azure.ai.vision.face.deviceattestation.handlers.Outcomes;
import com.azure.ai.vision.face.deviceattestation.store.StorageException;
import java.util.function.Supplier;
import com.azure.ai.vision.face.deviceattestation.handlers.RegisterHandler;
import com.azure.ai.vision.face.deviceattestation.handlers.TokenHandler;
import com.azure.ai.vision.face.deviceattestation.handlers.VerifyHandler;
import com.azure.ai.vision.face.deviceattestation.logging.AttestationLogger;
import com.azure.ai.vision.face.deviceattestation.logging.NullAttestationLogger;
import com.azure.ai.vision.face.deviceattestation.models.AttestationChallengeRequest;
import com.azure.ai.vision.face.deviceattestation.models.AttestationRegisterRequest;
import com.azure.ai.vision.face.deviceattestation.models.AttestationVerifyRequest;
import com.azure.ai.vision.face.deviceattestation.models.LivenessDigestRequest;
import com.azure.ai.vision.face.deviceattestation.models.SessionTokenRequest;
import com.azure.ai.vision.face.deviceattestation.services.ServerUtils;
import com.azure.ai.vision.face.deviceattestation.services.SessionData;
import com.azure.ai.vision.face.deviceattestation.services.WellKnown;
import com.azure.ai.vision.face.deviceattestation.store.ClusterStore;

/**
 * Handles every attestation endpoint plus the two /.well-known documents. Build
 * it once at startup with {@link #create} (cache it as a singleton); the host's
 * routes call its methods, so the routes never wire up storage or config and the
 * library never touches the environment.
 */
public final class AttestationService {

    private final AttestationContext ctx;

    private AttestationService(AttestationContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Configure the library and construct the service. Call once at startup and
     * cache the result as a singleton.
     *
     * @param config runtime configuration (app IDs, cert limits, …)
     * @param store  persistent session/certificate store implementation
     * @param logger telemetry sink; when null a no-op logger is used
     */
    public static AttestationService create(AttestationConfig config, ClusterStore store, AttestationLogger logger) {
        return new AttestationService(new AttestationContext(
                config, store, logger != null ? logger : NullAttestationLogger.INSTANCE));
    }

    /** Construct the service with the default no-op logger. */
    public static AttestationService create(AttestationConfig config, ClusterStore store) {
        return create(config, store, null);
    }

    /** POST /api/attestation/challenge */
    public HandlerOutcome challenge(AttestationChallengeRequest req) {
        return withStorageFailure(() -> new ChallengeHandler(ctx).handle(req));
    }

    /** POST /api/attestation/register */
    public HandlerOutcome register(AttestationRegisterRequest req) {
        return withStorageFailure(() -> new RegisterHandler(ctx).handle(req));
    }

    /** POST /api/attestation/verify */
    public HandlerOutcome verify(AttestationVerifyRequest req) {
        return withStorageFailure(() -> new VerifyHandler(ctx).handle(req));
    }

    /** POST /api/session/token */
    public HandlerOutcome sessionToken(SessionTokenRequest req) {
        return withStorageFailure(() -> new TokenHandler(ctx).handle(req));
    }

    /** POST /api/liveness/digest */
    public HandlerOutcome livenessDigest(LivenessDigestRequest req) {
        return withStorageFailure(() -> new DigestHandler(ctx).handle(req));
    }

    private HandlerOutcome withStorageFailure(Supplier<HandlerOutcome> action) {
        try {
            return action.get();
        } catch (StorageException error) {
            return Outcomes.fail(ctx.logger, "attestation", 503, "STORAGE_UNAVAILABLE", "Attestation storage unavailable", null, null, null);
        }
    }

    /** Whether a session record exists (created by {@link #saveSession}), by id. */
    public boolean sessionExists(String sid) {
        return ServerUtils.getSessionData(ctx, sid) != null;
    }

    /**
     * Liveness-completion signal for a session: whether the client has posted its
     * digest yet and, if so, the digest it submitted. Lets the host poll for
     * completion without reading the library's internal session shape.
     */
    public LivenessOutcome getLivenessOutcome(String sid) {
        SessionData session = ServerUtils.getSessionData(ctx, sid);
        if (session == null || !JsonData.getBool(session.data(), "digestCompleted")) {
            return new LivenessOutcome(false, null);
        }
        return new LivenessOutcome(true, JsonData.getString(session.data(), "digest"));
    }

    /** Seed a new session with the Face token; attestation state starts empty. */
    public String saveSession(String sid, String token) {
        return ServerUtils.saveToken(ctx, sid, token);
    }

    /** GET /.well-known/apple-app-site-association */
    public Map<String, Object> appleAppSiteAssociation() {
        return WellKnown.appleAppSiteAssociation(ctx.config);
    }

    /** GET /.well-known/assetlinks.json */
    public List<Object> androidAssetLinks() {
        return WellKnown.assetLinks(ctx.config);
    }
}
