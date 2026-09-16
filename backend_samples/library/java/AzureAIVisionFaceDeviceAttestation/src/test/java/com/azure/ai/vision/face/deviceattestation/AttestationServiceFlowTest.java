package com.azure.ai.vision.face.deviceattestation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.azure.ai.vision.face.deviceattestation.store.UpdateResult;

import org.junit.jupiter.api.Test;

import com.azure.ai.vision.face.deviceattestation.config.AttestationConfig;
import com.azure.ai.vision.face.deviceattestation.crypto.CryptoUtils;
import com.azure.ai.vision.face.deviceattestation.crypto.EcKeyPair;
import com.azure.ai.vision.face.deviceattestation.handlers.HandlerOutcome;
import com.azure.ai.vision.face.deviceattestation.models.AttestationChallengeRequest;
import com.azure.ai.vision.face.deviceattestation.models.AttestationChallengeSuccess;
import com.azure.ai.vision.face.deviceattestation.models.LivenessDigestBody;
import com.azure.ai.vision.face.deviceattestation.models.LivenessDigestData;
import com.azure.ai.vision.face.deviceattestation.models.LivenessDigestRequest;
import com.azure.ai.vision.face.deviceattestation.models.SessionTokenBody;
import com.azure.ai.vision.face.deviceattestation.models.SessionTokenRequest;
import com.azure.ai.vision.face.deviceattestation.models.SessionTokenSuccess;
import com.azure.ai.vision.face.deviceattestation.store.SessionRecord;

class AttestationServiceFlowTest {

    private static final String SID = "12345678-1234-1234-1234-123456789abc";

    private final TestClusterStore store = new TestClusterStore();
    private final AttestationService svc = AttestationService.create(AttestationConfig.builder().build(), store);

    @Test
    void casRejectsStaleSnapshotsAndDuplicateCreation() {
        var record = new SessionRecord("token", new HashMap<>());
        assertTrue(store.setSession(SID, record));
        assertFalse(store.setSession(SID, record));
        var snapshot = store.getSession(SID);
        snapshot.value().data.put("changed", true);
        assertFalse(store.getSession(SID).value().data.containsKey("changed"));
        assertEquals(UpdateResult.APPLIED, store.updateSession(SID, snapshot.version(), snapshot.value()));
        assertEquals(UpdateResult.CONFLICT, store.updateSession(SID, snapshot.version(), record));
        assertEquals(UpdateResult.MISSING_OR_EXPIRED, store.updateSession("missing", snapshot.version(), record));
    }

    @Test
    void concurrentSignedTokensHaveOneWinnerAndFailedWritesReleaseNothing() throws Exception {
        var keys = CryptoUtils.generateServerKeyPairEC();
        seedAuthenticatedSession(keys, keys, keys, "challenge");
        var body = new SessionTokenBody();
        body.encryptedData = CryptoUtils.encryptWithPublicKeyEC(Json.stringify(Maps.of(
                "challengeHash", "challenge", "clientId", "client-1", "system", "android")), keys.publicKey());
        body.signature = TestCrypto.signEc(body.encryptedData, keys.privateKey());
        var request = new SessionTokenRequest(SID, body);
        var barrier = new CyclicBarrier(2);
        store.afterSessionRead = () -> {
            try { barrier.await(5, TimeUnit.SECONDS); }
            catch (Exception error) { throw new RuntimeException(error); }
        };
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> svc.sessionToken(request));
            var second = pool.submit(() -> svc.sessionToken(request));
            var firstResult = first.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);
            assertEquals(1, (firstResult.ok ? 1 : 0) + (secondResult.ok ? 1 : 0));
            assertEquals(409, firstResult.ok ? secondResult.status : firstResult.status);
            var winner = assertInstanceOf(SessionTokenSuccess.class, (firstResult.ok ? firstResult : secondResult).body);
            assertEquals("face-token", Json.parseObject(CryptoUtils.decryptWithPrivateKeyEC(winner.encryptedData, keys.privateKey())).get("token"));
        } finally {
            pool.shutdownNow();
            store.afterSessionRead = null;
        }
        var snapshot = store.getSession(SID);
        snapshot.value().data.remove("authCompleted");
        store.updateSession(SID, snapshot.version(), snapshot.value());
        store.failWrites = true;
        var failure = svc.sessionToken(request);
        assertEquals(503, failure.status);
        assertFalse(failure.body instanceof SessionTokenSuccess);
        assertFalse(store.getSession(SID).value().data.containsKey("authCompleted"));
    }

    @Test
    void challengeHappyPath() {
        assertEquals(SID, svc.saveSession(SID, "face-token"));
        assertTrue(svc.sessionExists(SID));

        HandlerOutcome outcome = svc.challenge(new AttestationChallengeRequest(SID, "client-1", "android"));
        assertTrue(outcome.ok);
        assertEquals(200, outcome.status);

        AttestationChallengeSuccess body = assertInstanceOf(AttestationChallengeSuccess.class, outcome.body);
        assertEquals(64, body.challengeHash.length());
        assertEquals("client-1", body.clientId);
        assertEquals("android", body.system);
    }

    @Test
    void challengeMissingSessionId() {
        HandlerOutcome outcome = svc.challenge(new AttestationChallengeRequest(null, null, null));
        assertFalse(outcome.ok);
        assertEquals(400, outcome.status);
        assertEquals("MISSING_SESSION_ID", outcome.code);
    }

    @Test
    void challengeUnknownSession() {
        HandlerOutcome outcome = svc.challenge(new AttestationChallengeRequest(SID, "client-1", "android"));
        assertEquals(404, outcome.status);
        assertEquals("SESSION_NOT_FOUND", outcome.code);
    }

    @Test
    void tokenEndToEndDecryptsFaceToken() throws Exception {
        EcKeyPair clientAuth = CryptoUtils.generateServerKeyPairEC();
        EcKeyPair clientEnc = CryptoUtils.generateServerKeyPairEC();
        EcKeyPair serverEnc = CryptoUtils.generateServerKeyPairEC();
        seedAuthenticatedSession(clientAuth, clientEnc, serverEnc, "abc123");

        String payload = Json.stringify(Maps.of("challengeHash", "abc123", "clientId", "client-1", "system", "android"));
        String encryptedData = CryptoUtils.encryptWithPublicKeyEC(payload, serverEnc.publicKey());
        String signature = TestCrypto.signEc(encryptedData, clientAuth.privateKey());

        SessionTokenBody body = new SessionTokenBody();
        body.encryptedData = encryptedData;
        body.signature = signature;

        HandlerOutcome outcome = svc.sessionToken(new SessionTokenRequest(SID, body));
        assertTrue(outcome.ok, () -> "code=" + outcome.code + " msg=" + outcome.message);

        SessionTokenSuccess success = assertInstanceOf(SessionTokenSuccess.class, outcome.body);
        String decrypted = CryptoUtils.decryptWithPrivateKeyEC(success.encryptedData, clientEnc.privateKey());
        Map<String, Object> response = Json.parseObject(decrypted);
        assertEquals("face-token", response.get("token"));
    }

    @Test
    void digestEndToEndRecordsOutcome() throws Exception {
        EcKeyPair clientAuth = CryptoUtils.generateServerKeyPairEC();
        EcKeyPair clientEnc = CryptoUtils.generateServerKeyPairEC();
        EcKeyPair serverEnc = CryptoUtils.generateServerKeyPairEC();
        seedAuthenticatedSession(clientAuth, clientEnc, serverEnc, "abc123");

        String payload = Json.stringify(Maps.of("cid", "client-1", "os", "android", "digest", "digest-xyz"));
        var snapshot = store.getSession(SID);
        snapshot.value().data.put("authCompleted", true);
        assertEquals(com.azure.ai.vision.face.deviceattestation.store.UpdateResult.APPLIED,
            store.updateSession(SID, snapshot.version(), snapshot.value()));
        String encryptedData = CryptoUtils.encryptWithPublicKeyEC(payload, serverEnc.publicKey());
        String signature = TestCrypto.signEc(encryptedData, clientAuth.privateKey());

        LivenessDigestBody body = new LivenessDigestBody();
        body.encryptedData = encryptedData;
        body.signature = signature;

        HandlerOutcome outcome = svc.livenessDigest(new LivenessDigestRequest(SID, body));
        assertTrue(outcome.ok, () -> "code=" + outcome.code + " msg=" + outcome.message);

        LivenessDigestData data = assertInstanceOf(LivenessDigestData.class, outcome.data);
        assertEquals("digest-xyz", data.clientDigest);

        LivenessOutcome livenessOutcome = svc.getLivenessOutcome(SID);
        assertTrue(livenessOutcome.completed);
        assertEquals("digest-xyz", livenessOutcome.clientDigest);
    }

    private void seedAuthenticatedSession(EcKeyPair clientAuth, EcKeyPair clientEnc, EcKeyPair serverEnc, String challengeHash) {
        Map<String, Object> data = new HashMap<>();
        data.put("serverKeyGenerated", true);
        data.put("certRegistered", true);
        data.put("system", "android");
        data.put("challengeHash", challengeHash);
        data.put("clientId", "client-1");
        data.put("clientAuthPublicKey", clientAuth.publicKey());
        data.put("clientEncryptionPublicKey", clientEnc.publicKey());
        data.put("serverEncryptionPrivateKey", serverEnc.privateKey());
        data.put("serverEncryptionPublicKey", serverEnc.publicKey());
        store.setSession(SID, new SessionRecord("face-token", data));
    }
}
