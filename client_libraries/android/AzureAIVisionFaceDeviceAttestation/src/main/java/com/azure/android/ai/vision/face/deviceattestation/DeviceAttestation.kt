package com.azure.android.ai.vision.face.deviceattestation

import android.content.Context
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Public entry points for the cert-based QuickLink auth flow.
 *
 * Called from [AppCenterActivity] / [ResultScreen]:
 *   - [initialize]   — configure the library and warm up Play Integrity
 *   - [startSession]   — run the full attest flow and return an [AttestationSession]
 *                        that privately owns the challengeHash + session keys
 *   - [currentSession] — the single active [AttestationSession], if any
 *
 * Per-session token exchange and liveness digest submission live on
 * [AttestationSession]; app-static config lives here.
 *
 * Where the work happens:
 *   - HTTP plumbing         → [AuthHttpClient]
 *   - Per-endpoint wrappers → [fetchAttestationChallenge], [verifyAttestation],
 *                             [registerAttestation], [requestSessionToken],
 *                             [submitLivenessDigest]
 *   - Play Integrity        → [PlayIntegrityTokenProvider]
 *   - Ephemeral encryption  → [EphemeralEncryptionCertManager]
 *   - Long-lived auth certs → [CertificateManager]
 *   - Attestation flow glue → [AttestationFlow]
 *   - Encrypt+sign envelope → [EncryptedPayloadEnvelope]
 */
object DeviceAttestation {

    // Only one attestation session is active at a time. Starting a new one
    // replaces (and closes) any previous session. Held so a later screen (e.g.
    // the result screen) can re-resolve it via [currentSession] without holding
    // the crypto objects itself.
    @Volatile
    private var activeSession: AttestationSession? = null

    // Session ids are server-issued UUIDs (the App Link `s` value). Rejecting
    // anything else up front is defense-in-depth: the value arrives already
    // URL-decoded (via Uri.getQueryParameter), so a crafted `s` that slipped
    // through would smuggle extra query parameters into the endpoint URLs.
    private val SESSION_ID_REGEX =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

    // ---------------------------------------------------------------------
    // Result types
    // ---------------------------------------------------------------------

    /** Result of [startSession]. */
    sealed class StartSessionResult {
        /** Attestation succeeded; [session] is ready for token + digest calls. */
        data class Success(val session: AttestationSession) : StartSessionResult()
        data class Error(val code: Int, val message: String) : StartSessionResult()
        data class Exception(val exception: Throwable) : StartSessionResult()
    }

    // ---------------------------------------------------------------------
    // Public utilities
    // ---------------------------------------------------------------------

    /**
     * Configures the library and pre-warms the Play Integrity provider. Must be
     * called once (typically from app startup) before any other entry point. The
     * host app supplies the values — usually read from its own `BuildConfig` — so
     * this library carries no build-time coupling of its own.
     *
     * @param context             Android context used to warm up Play Integrity
     * @param livenessHost        host of the liveness backend, e.g. `example.com`
     * @param cloudProjectNumber  Play Integrity cloud project number
     * @param endpoints           server-relative endpoint paths; defaults match
     *                            the reference backend
     */
    suspend fun initialize(
        context: Context,
        livenessHost: String,
        cloudProjectNumber: Long,
        endpoints: DeviceAttestationEndpoints = DeviceAttestationEndpoints()
    ) {
        DeviceAttestationConfig.set(livenessHost, cloudProjectNumber, endpoints)
        // Warm up here so the registration round trip doesn't pay the
        // prepareIntegrityToken cost on the critical path.
        PlayIntegrityTokenProvider.warmup(context)
    }

    // ---------------------------------------------------------------------
    // Sessions
    // ---------------------------------------------------------------------

    /**
     * Runs the full attestation flow and, on success, returns a per-session
     * [AttestationSession]:
     *   1. fetch challenge hash from /attestation/challenge
     *   2. ensure local hardware-backed auth cert exists
     *   3. ask the server if it already knows our cert (/attestation/verify)
     *   4. if not, attest + register the cert (/attestation/register)
     *   5. hand the server encryption pubkey + ephemeral privkey to the returned
     *      session, which keeps them (and the challengeHash) private
     *
     * On success the session becomes the single active session (re-resolve it
     * via [currentSession]). Its ephemeral key is wiped automatically after
     * [AttestationSession.submitLivenessDigest], and starting another session
     * clears this one — so callers never need to release it explicitly.
     */
    suspend fun startSession(
        context: Context,
        sessionId: String,
        deviceId: String
    ): StartSessionResult {
        if (sessionId.isEmpty()) {
            return StartSessionResult.Error(-1, "sessionId is empty")
        }
        if (!SESSION_ID_REGEX.matches(sessionId)) {
            return StartSessionResult.Error(-1, "sessionId is not a valid session id")
        }

        return try {
            val challengeHash = when (val r = AttestationFlow.fetchChallenge(sessionId, deviceId)) {
                is AttestationFlow.ChallengeResult.Success -> r.challengeHash
                is AttestationFlow.ChallengeResult.Failure -> return StartSessionResult.Error(r.code, r.message)
            }

            // Generate the local auth cert before talking to the server again.
            // For first-time generation we bind the server's challengeHash into
            // the attestation extension; renewals do the same when triggered
            // inside AttestationFlow.obtainSessionKeys.
            val keyExisted = CertificateManager.hasCertificate(deviceId)
            CertificateManager.ensureKeyPair(
                deviceId,
                attestationChallenge = if (!keyExisted) challengeHash else null
            )

            val (serverPubKeyPem, ephemeralPrivKey) = when (
                val keys = AttestationFlow.obtainSessionKeys(context, sessionId, deviceId, challengeHash, keyExisted)
            ) {
                is AttestationFlow.SessionKeysResult.Success -> keys.serverPublicKeyPem to keys.ephemeralPrivateKey
                is AttestationFlow.SessionKeysResult.Failure -> return keys.toStartSessionResult()
            }

            val serverPubKey: PublicKey = CryptoHelper.parsePublicKey(serverPubKeyPem)
            val ephemeralKey: PrivateKey = ephemeralPrivKey
            val session = AttestationSession(
                sessionId = sessionId,
                deviceId = deviceId,
                challengeHash = challengeHash,
                serverEncryptionPublicKey = serverPubKey,
                ephemeralEncryptionPrivateKey = ephemeralKey
            )
            // Only one active session: replace (and clear) any previous one so
            // its ephemeral key doesn't linger if that session was abandoned.
            activeSession?.cleanup()
            activeSession = session
            StartSessionResult.Success(session)
        } catch (e: Exception) {
            StartSessionResult.Exception(e)
        }
    }

    /** The single active session, or null if none has been started / it was closed. */
    fun currentSession(): AttestationSession? = activeSession

    /** Clears the active session. Called by [AttestationSession.cleanup]. */
    internal fun clearSession(session: AttestationSession) {
        // Guard against a newer session having already replaced this one.
        if (activeSession === session) activeSession = null
    }
}
