package com.microsoft.azure.ai.vision.facelivenessdetectorsample.token

object FaceSessionToken {
    var mFaceApiVersion: String = "v1.2"
    var sessionToken: String = ""
    var sessionId: String = ""
    var sessionSetInClientVerifyImage: ByteArray? = null
    var isVerifyImage = false
    var livenessStatus: String? = null
    var verificationStatus: String? = null
    var verificationMatchConfidence: String? = null
}