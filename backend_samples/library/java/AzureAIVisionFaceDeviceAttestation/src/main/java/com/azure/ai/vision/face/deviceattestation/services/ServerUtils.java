package com.azure.ai.vision.face.deviceattestation.services;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.azure.ai.vision.face.deviceattestation.AttestationContext;
import com.azure.ai.vision.face.deviceattestation.Maps;
import com.azure.ai.vision.face.deviceattestation.store.SessionRecord;
import com.azure.ai.vision.face.deviceattestation.store.Snapshot;
import com.azure.ai.vision.face.deviceattestation.store.UpdateResult;

/**
 * Session-domain storage helpers. Every function receives the
 * {@link AttestationContext} and delegates ALL persistence (connection, key
 * schema, serialization, TTL) to the injected store; this layer keeps only the
 * session-domain concerns (UUID validation, events).
 */
public final class ServerUtils {

    private static final Pattern UUID_REGEX =
            Pattern.compile("^[\\da-f]{8}-([\\da-f]{4}-){3}[\\da-f]{12}$", Pattern.CASE_INSENSITIVE);

    /** Seed a session with its token (empty state; fresh TTL owned by the store). */
    public static String saveToken(AttestationContext ctx, String sid, String token) {
        boolean stored = ctx.store.setSession(sid, new SessionRecord(token, new HashMap<>()));
        return stored ? sid : null;
    }

    /** Read the {token, data, sid} record, or null if invalid/absent/expired. */
    public static SessionData getSessionData(AttestationContext ctx, String sid) {
        if (sid == null || sid.isEmpty() || !UUID_REGEX.matcher(sid).matches()) {
            ctx.logger.trackEvent("SessionStore.GetSessionFail", Maps.of("reason", "INVALID_UUID", "sid", sid), null);
            return null;
        }
        Snapshot<SessionRecord> record = ctx.store.getSession(sid);
        if (record == null) {
            return null;
        }
        return new SessionData(record.value().token, record.value().data, sid, record.version());
    }

    /** Write back {token, data}, preserving the existing TTL. */
    public static boolean updateSessionData(AttestationContext ctx, String sid, String token, Map<String, Object> data, String expectedVersion) {
        if (sid == null || sid.isEmpty() || !UUID_REGEX.matcher(sid).matches()) {
            ctx.logger.trackEvent("SessionStore.UpdateFail", Maps.of("reason", "INVALID_UUID", "sid", sid), null);
            return false;
        }
        return ctx.store.updateSession(sid, expectedVersion, new SessionRecord(token, data)) == UpdateResult.APPLIED;
    }

    private ServerUtils() {
    }
}
