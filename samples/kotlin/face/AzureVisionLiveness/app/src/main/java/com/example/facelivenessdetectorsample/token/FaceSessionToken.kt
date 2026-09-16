package com.microsoft.azure.ai.vision.facelivenessdetectorsample.token

object FaceSessionToken {
    var mFaceApiVersion: String = "v1.2"
    var sessionToken: String = ""
    var sessionId: String = ""
    var callbackUrl: String? = null
    // Liveness backend host resolved from the inbound App Link URL, validated
    // against BuildConfig.LIVENESS_HOSTS. Targets attestation for this session
    // and same-origin-validates the callbackUrl before it is followed.
    var livenessHost: String = ""
    var quickLink: Boolean = false
    var sessionSetInClientVerifyImage: ByteArray? = null
    var isVerifyImage = false
    var livenessStatus: String? = null
    var verificationStatus: String? = null
    var verificationMatchConfidence: String? = null
    var deviceCorrelationIdInClient: String? = null
}