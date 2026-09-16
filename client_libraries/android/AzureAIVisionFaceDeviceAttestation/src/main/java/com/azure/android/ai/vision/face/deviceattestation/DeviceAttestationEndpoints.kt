package com.azure.android.ai.vision.face.deviceattestation

/**
 * Host-relative paths for the auth-flow endpoints, resolved against
 * `https://<livenessHost>/`.
 *
 * Defaults match the reference backend; override any subset via
 * [DeviceAttestation.initialize] when a deployment uses a different routing
 * scheme (e.g. a different API prefix or versioned paths).
 *
 * Paths must not begin with a leading `/`.
 */
data class DeviceAttestationEndpoints(
    val attestationChallenge: String = "api/attestation/challenge",
    val attestationVerify: String = "api/attestation/verify",
    val attestationRegister: String = "api/attestation/register",
    val sessionToken: String = "api/session/token",
    val livenessDigest: String = "api/liveness/digest",
)
