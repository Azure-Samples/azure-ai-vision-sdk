package com.microsoft.azurevisionliveness

import android.content.Context
import com.azure.android.ai.vision.face.deviceattestation.AttestationSession
import com.azure.android.ai.vision.face.deviceattestation.DeviceAttestation
import com.azure.android.ai.vision.face.deviceattestation.DeviceAttestationEndpoints
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.token.FaceSessionToken
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.utils.getDeviceIdExt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Drives the cert-based QuickLink auth flow that runs after the app opens via
 * an Android App Link URL. Mirrors iOS's `processAASAUrl` (the iOS-side
 * equivalent — AASA / Apple App Site Association is Apple's protocol; the
 * Android equivalent is Android App Links / Digital Asset Links). The
 * Activity owns the progress UI, this function owns the chain of network +
 * crypto steps.
 *
 * Steps:
 *   1. Warm up the Play Integrity provider
 *   2. Run the attestation flow (challenge → verify → register if needed)
 *   3. Exchange the auth state for a session token (encrypted envelope)
 *   4. Install the token + correlation id into [FaceSessionToken]
 *
 * Threading: network work happens on [Dispatchers.IO]; the three callbacks
 * are always invoked on [Dispatchers.Main] so they can mutate Compose state
 * directly.
 *
 * @param context  Android context for Play Integrity warmup
 * @param sParam   Session id pulled off the App Link URL's `s` query parameter
 * @param livenessHost  Backend host resolved from the inbound App Link URL and
 *                  already validated against BuildConfig.LIVENESS_HOSTS; the
 *                  attestation library targets this host for the session
 * @param onStage  Called with each progress label
 * @param onError  Called with a user-facing message if any step fails
 * @param onSuccess Called after the session token is installed; the caller
 *                  typically swaps to the liveness UI here
 */
suspend fun runAppLinkAuthFlow(
    context: Context,
    sParam: String,
    livenessHost: String,
    onStage: (String) -> Unit,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) = withContext(Dispatchers.IO) {
    val deviceId = context.getDeviceIdExt()
    val deviceUUID = UUID(deviceId, deviceId).toString()

    // Hand the app's build-time config to the attestation library so it stays
    // decoupled from this app's BuildConfig. Endpoint paths are passed
    // explicitly so the app owns the backend's routing scheme. This also warms
    // up the Play Integrity provider so the registration round trip doesn't pay
    // the prepareIntegrityToken cost on the critical path.
    DeviceAttestation.initialize(
        context = context,
        livenessHost = livenessHost,
        cloudProjectNumber = BuildConfig.CLOUD_PROJECT_NUMBER,
        endpoints = DeviceAttestationEndpoints(
            attestationChallenge = "api/attestation/challenge",
            attestationVerify = "api/attestation/verify",
            attestationRegister = "api/attestation/register",
            sessionToken = "api/session/token",
            livenessDigest = "api/liveness/digest"
        )
    )

    withContext(Dispatchers.Main) { onStage("Verifying device…") }

    when (val start = DeviceAttestation.startSession(context, sessionId = sParam, deviceId = deviceUUID)) {
        is DeviceAttestation.StartSessionResult.Success -> {
            withContext(Dispatchers.Main) { onStage("Fetching session token…") }
            fetchAndInstallSessionToken(
                session = start.session,
                onError = onError,
                onSuccess = onSuccess
            )
        }
        is DeviceAttestation.StartSessionResult.Error -> {
            println("Attestation error ${start.code}: ${start.message}")
            withContext(Dispatchers.Main) {
                onError("Couldn't start your session (${start.code}). ${start.message}")
            }
        }
        is DeviceAttestation.StartSessionResult.Exception -> {
            println("Attestation exception: ${start.exception.message}")
            start.exception.printStackTrace()
            withContext(Dispatchers.Main) {
                onError("Couldn't start your session. ${start.exception.message ?: "Unknown error"}")
            }
        }
    }
}

/**
 * Step 2 of the flow: hit `/session/token`, decrypt the token, and stash it
 * (plus the device correlation id) on [FaceSessionToken]. Separate function
 * mainly so the success-branch nesting in [runAppLinkAuthFlow] doesn't grow
 * past one level.
 *
 * Authenticates by ECDSA signature over the encrypted blob — no fresh
 * attestation here (that already happened during cert registration).
 */
private suspend fun fetchAndInstallSessionToken(
    session: AttestationSession,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    when (val tokenResult = session.fetchSessionToken()) {
        is AttestationSession.SessionTokenResult.Success -> {
            FaceSessionToken.sessionToken = tokenResult.token
            FaceSessionToken.sessionSetInClientVerifyImage = null
            FaceSessionToken.livenessStatus = null
            FaceSessionToken.verificationStatus = null
            FaceSessionToken.verificationMatchConfidence = null
            FaceSessionToken.quickLink = true
            FaceSessionToken.deviceCorrelationIdInClient = session.deviceId
            withContext(Dispatchers.Main) { onSuccess() }
        }
        is AttestationSession.SessionTokenResult.Error -> {
            println("Error response code: ${tokenResult.code}, message: ${tokenResult.message}")
            withContext(Dispatchers.Main) {
                onError("Couldn't fetch session token (${tokenResult.code}). ${tokenResult.message}")
            }
        }
        is AttestationSession.SessionTokenResult.Exception -> {
            tokenResult.exception.printStackTrace()
            withContext(Dispatchers.Main) {
                onError("Couldn't fetch session token. ${tokenResult.exception.message ?: "Unknown error"}")
            }
        }
    }
}
