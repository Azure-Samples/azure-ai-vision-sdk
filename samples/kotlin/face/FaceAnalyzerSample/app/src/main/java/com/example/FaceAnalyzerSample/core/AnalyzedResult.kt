import android.os.Parcelable
import com.azure.android.ai.vision.faceanalyzer.LivenessFailureReason
import com.azure.android.ai.vision.faceanalyzer.LivenessStatus
import com.azure.android.ai.vision.faceanalyzer.RecognitionStatus
import kotlinx.parcelize.Parcelize

//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

/***
 * Model class
 */
@Parcelize
data class AnalyzedResult(val livenessStatus: LivenessStatus,
                          val failureReason: LivenessFailureReason,
                          val verificationStatus: RecognitionStatus,
                          val confidence: Float,
                          val resultId: String,
                          val digest: String) :
    Parcelable