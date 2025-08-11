package com.microsoft.azure.ai.vision.facelivenessdetectorsample.dynamicfeature

import java.util.function.Consumer

class FaceLivenessDetectorModel(
    val sessionAuthorizationToken: String,
    val verifyImageFileContent: ByteArray?,
    val deviceCorrelationId: String?,
    val userCorrelationId: String?,
    val onSuccess: Consumer<Any>,
    val onError: Consumer<Any>
)