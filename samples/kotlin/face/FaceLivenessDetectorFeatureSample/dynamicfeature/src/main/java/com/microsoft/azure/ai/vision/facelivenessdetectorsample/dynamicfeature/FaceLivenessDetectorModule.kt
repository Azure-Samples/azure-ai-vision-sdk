package com.microsoft.azure.ai.vision.facelivenessdetectorsample.dynamicfeature

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.azure.android.ai.vision.face.ui.FaceLivenessDetector
import com.azure.android.ai.vision.face.ui.LivenessDetectionError
import com.azure.android.ai.vision.face.ui.LivenessDetectionSuccess
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallHelper

@Composable
fun FaceLivenessDetectorModule(
    faceLivenessDetectorModel: FaceLivenessDetectorModel
) {
    val onSuccess: (LivenessDetectionSuccess) -> Unit = {
        faceLivenessDetectorModel.onSuccess.accept(it)
    }
    val onError: (LivenessDetectionError) -> Unit = {
        faceLivenessDetectorModel.onError.accept(it)
    }
    val context = LocalContext.current
    SplitCompat.install(context)
    SplitInstallHelper.loadLibrary(context, "Azure-AI-Vision-Native")
    SplitInstallHelper.loadLibrary(context, "onnxruntime")
    SplitInstallHelper.loadLibrary(context, "Azure-AI-Vision-Extension-Face")
    SplitInstallHelper.loadLibrary(context, "Azure-AI-Vision-JNI")

    FaceLivenessDetector(
        faceLivenessDetectorModel.sessionAuthorizationToken,
        faceLivenessDetectorModel.verifyImageFileContent,
        faceLivenessDetectorModel.deviceCorrelationId,
        onSuccess,
        onError)
}