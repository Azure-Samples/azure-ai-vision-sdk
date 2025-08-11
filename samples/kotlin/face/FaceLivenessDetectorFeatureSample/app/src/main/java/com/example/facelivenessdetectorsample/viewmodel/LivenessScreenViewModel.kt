package com.microsoft.azure.ai.vision.facelivenessdetectorsample.viewmodel

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlin.reflect.full.memberProperties


data class LivenessResultReceived (
    val livenessDetectionSuccessReceived: Boolean = false,
    val livenessDetectionErrorReceived: Boolean = false
)
class LivenessScreenViewModel(sharedPreferences: SharedPreferences) : ViewModel() {

    private val _analyzedResultResponse =
        // TODO: optimize this typing. Reduce to AnalyzedResultType, as the view doesn't need the result.
        MutableStateFlow(LivenessResultReceived())
    val analyzedResultResponse: StateFlow<LivenessResultReceived> = _analyzedResultResponse


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

    fun onSuccess(livenessDetectionSuccess: Any) {
        val editor = mSharedPreferences.edit()
        val successClass = livenessDetectionSuccess::class
        val resultIdProp = successClass.memberProperties.find{ it.name == "resultId" }
        resultIdProp?.let {
            val resultId = resultIdProp.getter.call(livenessDetectionSuccess) as String
            editor.putString(SharedPrefKeys.LAST_SESSION_APIM_REQ_ID, resultId)
        }
        editor.commit()
        val livenessResult = LivenessResultReceived(true, false)
        //Query the server for result.  It should be performed in the backend server
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
        if (faceApiEndpoint.isNotBlank() && faceApiKey.isNotBlank()) {
            fetchFaceAPILivenssResult(onResultFetched)
        }
        else {
            onResultFetched()
        }
    }

    fun onError(livenessDetectionError: Any) {
        val livenessResult = LivenessResultReceived(false, true)
        _analyzedResultResponse.value = livenessResult
        val errorClass = livenessDetectionError::class
        val livenessErrorProp = errorClass.memberProperties.find{it.name == "livenessError"}
        val recogErrorProp = errorClass.memberProperties.find{it.name == "recognitionError"}
        val livenessErrorStr = livenessErrorProp?.let {
            val lerror = it.getter.call(livenessDetectionError)
            if(lerror != null){
                lerror.toString()
            }
            else{
                ""
            }
        }
        val recogErrorStr = recogErrorProp?.let {
            val rerror = it.getter.call(livenessDetectionError)
            if(rerror != null){
                rerror.toString()
            }
            else{
                ""
            }
        }
        val result = ResultData(
            livenessStatus = livenessErrorStr,
            livenessFailureReason = livenessErrorStr,
            verificationStatus = recogErrorStr,
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
