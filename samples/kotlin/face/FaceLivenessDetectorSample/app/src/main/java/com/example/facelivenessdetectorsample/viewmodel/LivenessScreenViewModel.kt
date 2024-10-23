package com.example.facelivenessdetectorsample.viewmodel

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.azure.android.ai.vision.face.ui.LivenessDetectionError
import com.azure.android.ai.vision.face.ui.LivenessDetectionSuccess
import com.example.facelivenessdetectorsample.models.ResultData
import com.example.facelivenessdetectorsample.utils.SharedPrefKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


data class LivenessResult (
    val livenessDetectionSuccess: LivenessDetectionSuccess? = null,
    val livenessDetectionError: LivenessDetectionError? = null
)
class LivenessScreenViewModel(sharedPreferences: SharedPreferences) : ViewModel() {

    private val _analyzedResultResponse =
        // TODO: optimize this typing. Reduce to AnalyzedResultType, as the view doesn't need the result.
        MutableStateFlow(LivenessResult())
    val analyzedResultResponse: StateFlow<LivenessResult> = _analyzedResultResponse


    private val _shouldShowLivenessAnalyze = MutableStateFlow(false)
    val shouldShowLivenessAnalyze: StateFlow<Boolean> = _shouldShowLivenessAnalyze

    private val _resultData = MutableStateFlow(ResultData())
    val resultData: StateFlow<ResultData> = _resultData

    private val _initialPermissionState = MutableStateFlow(false)
    val initialPermissionState: StateFlow<Boolean> = _initialPermissionState

    var faceApiEndpoint by mutableStateOf(
        sharedPreferences.getString(
            SharedPrefKeys.FACE_API_ENDPOINT, ""
        ) ?: ""
    )
        private set

    var setImageInClient by mutableStateOf(
        sharedPreferences.getBoolean(
            SharedPrefKeys.SET_IMAGE_IN_CLIENT, false
        )
    )
        private set

    private var experimentalMode by mutableStateOf(
        sharedPreferences.getBoolean(
            SharedPrefKeys.EXPERIMENTAL_MODE, false
        )
    )

    fun propertyBag(): Map<String, String>? {
        return if (experimentalMode) {
            mapOf("ExperimentalMode" to "true")
        } else {
            null
        }
    }

    fun onSuccess(livenessDetectionSuccess: LivenessDetectionSuccess) {
        val livenessResult = LivenessResult(livenessDetectionSuccess, null)
        _analyzedResultResponse.value = livenessResult
        val result = ResultData(
            livenessStatus = livenessDetectionSuccess.livenessStatus.toString(),
            livenessFailureReason = null,
            verificationStatus = livenessDetectionSuccess.recognitionResult.recognitionStatus.toString(),
            verificationConfidence = livenessDetectionSuccess.recognitionResult.matchConfidence.toString()
        )
        _resultData.value = result
    }

    fun onError(livenessDetectionError: LivenessDetectionError) {
        val livenessResult = LivenessResult(null, livenessDetectionError)
        _analyzedResultResponse.value = livenessResult
        val result = ResultData(
            livenessStatus = livenessDetectionError.livenessError.toString(),
            livenessFailureReason = livenessDetectionError.livenessError.toString(),
            verificationStatus = livenessDetectionError.recognitionError.toString(),
            verificationConfidence = null
        )
        _resultData.value = result
    }

    fun showLivenessAnalyze() {
        _shouldShowLivenessAnalyze.value = true
    }

    fun hideLivenessAnalyze() {
        _shouldShowLivenessAnalyze.value = false
    }

    fun onInitialStateFetch(initialState: Boolean) {
        _initialPermissionState.value = initialState
    }
}
