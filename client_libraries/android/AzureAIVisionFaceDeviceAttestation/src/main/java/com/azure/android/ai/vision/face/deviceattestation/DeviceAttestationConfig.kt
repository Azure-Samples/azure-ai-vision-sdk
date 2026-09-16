package com.azure.android.ai.vision.face.deviceattestation

/**
 * Runtime configuration for the device-attestation library.
 *
 * Keeps the library decoupled from the host app's `BuildConfig`: the app is
 * expected to read these values from wherever it likes (typically its own
 * `BuildConfig`) and hand them over once via [DeviceAttestation.initialize]
 * before any flow runs.
 */
internal object DeviceAttestationConfig {

    @Volatile
    private var livenessHost: String? = null

    @Volatile
    private var cloudProjectNumber: Long? = null

    @Volatile
    private var endpointPaths: DeviceAttestationEndpoints = DeviceAttestationEndpoints()

    /** Installs the caller-supplied configuration. Safe to call more than once. */
    fun set(
        livenessHost: String,
        cloudProjectNumber: Long,
        endpoints: DeviceAttestationEndpoints
    ) {
        require(livenessHost.isNotBlank()) { "livenessHost must not be blank" }
        this.livenessHost = livenessHost
        this.cloudProjectNumber = cloudProjectNumber
        this.endpointPaths = endpoints
    }

    /** Play Integrity cloud project number. */
    val cloudProject: Long
        get() = requireInitialized(cloudProjectNumber)

    /** Caller-configured (or default) endpoint paths. */
    val endpoints: DeviceAttestationEndpoints
        get() = endpointPaths

    /**
     * Absolute URL for a host-relative [path] such as `api/attestation/challenge`,
     * resolved as `https://<livenessHost>/<path>`.
     */
    fun endpointUrl(path: String): String =
        "https://${requireInitialized(livenessHost)}/$path"

    private fun <T : Any> requireInitialized(value: T?): T =
        value ?: error("DeviceAttestation.initialize(...) must be called before use")
}
