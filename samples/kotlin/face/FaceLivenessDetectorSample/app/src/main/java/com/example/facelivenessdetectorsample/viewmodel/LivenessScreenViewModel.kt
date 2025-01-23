package com.microsoft.azure.ai.vision.facelivenessdetectorsample.viewmodel

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azure.android.ai.vision.face.ui.LivenessDetectionError
import com.azure.android.ai.vision.face.ui.LivenessDetectionSuccess
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.models.ResultData
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.token.FaceSessionToken
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.utils.SharedPrefKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.Charset
import javax.net.ssl.HttpsURLConnection


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

    val mSharedPreferences = sharedPreferences
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

    private var fetchTokenJob: Job? = null
    var isWaitingForResult by mutableStateOf(false)
        private set
    var faceApiKey by mutableStateOf(
        sharedPreferences.getString(SharedPrefKeys.FACE_API_KEY, "") ?: ""
    )
        private set
    fun fetchFaceAPILivenssResult(
        onResultFetched: (() -> Unit)? = null){
        fetchTokenJob?.cancel()

        onResultFetched?.let {
            isWaitingForResult = true
        }

        fetchTokenJob = viewModelScope.launch(context = Dispatchers.IO) {
            var url: URL?
            var urlConnection: HttpsURLConnection? = null
            val type = if (FaceSessionToken.isVerifyImage) "detectLivenessWithVerify" else "detectLiveness"
            if (faceApiEndpoint.isNotBlank() && faceApiKey.isNotBlank()) {
                try {
                    //fetch result url
                    url = URL("$faceApiEndpoint/face/${FaceSessionToken.mFaceApiVersion}/$type-sessions/${FaceSessionToken.sessionId}")
                    val charset: Charset = Charset.forName("UTF-8")
                    urlConnection = url.openConnection() as HttpsURLConnection
                    urlConnection.setRequestProperty("Ocp-Apim-Subscription-Key", faceApiKey)
                    urlConnection.setRequestProperty(
                        "Content-Type", "application/json; charset=$charset"
                    )
                    urlConnection.requestMethod = "GET"
                    val (reader, throwable) = try {
                        Pair(BufferedReader(InputStreamReader(urlConnection.inputStream)), null)
                    } catch (t: Throwable) {
                        Pair(BufferedReader(InputStreamReader(urlConnection.errorStream)), t)
                    }
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    // Parse the JSON response
                    val jsonResponse = response.toString()

                    if (throwable == null) {
                        val jsonObject = JSONObject(jsonResponse)
                        val results = jsonObject.getJSONObject("results")
                        val attempts = results.getJSONArray("attempts")
                        val attempt = attempts.getJSONObject(0)
                        val result = attempt.getJSONObject("result")
                        FaceSessionToken.livenessStatus = result.getString("livenessDecision")
                        if (FaceSessionToken.isVerifyImage) {
                            val verificationResult = result.getJSONObject("verifyResult")
                            FaceSessionToken.verificationStatus = verificationResult.getBoolean("isIdentical").toString()
                            FaceSessionToken.verificationMatchConfidence = verificationResult.getDouble("matchConfidence").toString()
                        } 
                    } else {
                        Log.d(
                            "Face API Session Result Fetched",
                            "Status: ${urlConnection.responseCode} ${urlConnection.responseMessage}"
                        )
                        Log.d("Face API Session Fetched", "Body: $jsonResponse")
                        throw throwable
                    }
                } catch (ex: Exception) {
                    Log.wtf("Face API Session Result Fetched", "Error: ${ex.message}")
                    ex.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) {
                        onResultFetched?.invoke()
                    }
                    isWaitingForResult = false
                    urlConnection?.disconnect()
                }
            }
        }
    }

    fun onSuccess(livenessDetectionSuccess: LivenessDetectionSuccess) {
        val editor = mSharedPreferences.edit()
        editor.putString(SharedPrefKeys.LAST_SESSION_APIM_REQ_ID, livenessDetectionSuccess.resultId)
        editor.commit()
        val livenessResult = LivenessResult(livenessDetectionSuccess, null)
       
        // Once the session is completed, the client does not receive the outcome whether face is live or spoof.
        // You can query the result from your backend service by calling the sessions results API
        // https://aka.ms/face/liveness-session/get-liveness-session-result
        val onResultFetched: () -> Unit = {
            _analyzedResultResponse.value = livenessResult
            val result = ResultData(
                livenessStatus = FaceSessionToken.livenessStatus,
                livenessFailureReason = null,
                verificationStatus = FaceSessionToken.verificationStatus,
                verificationConfidence = FaceSessionToken.verificationMatchConfidence
            )
            _resultData.value = result
        }
        fetchFaceAPILivenssResult(onResultFetched)


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
