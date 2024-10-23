package com.example.facelivenessdetectorsample.models

import kotlinx.serialization.Serializable

@Serializable
data class ResultData(
    val livenessStatus: String? = null,
    val livenessFailureReason: String? = null,
    val verificationStatus: String? = null,
    val verificationConfidence: String? = null
)
