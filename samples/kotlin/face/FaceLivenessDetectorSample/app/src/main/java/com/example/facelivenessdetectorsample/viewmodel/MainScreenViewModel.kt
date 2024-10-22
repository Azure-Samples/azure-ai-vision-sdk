package com.example.facelivenessdetectorsample.viewmodel

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.facelivenessdetectorsample.token.FaceSessionToken
import com.example.facelivenessdetectorsample.utils.SharedPrefKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.URL
import java.nio.charset.Charset
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

class MainScreenViewModel(sharedPreferences: SharedPreferences) : ViewModel() {

    private var fetchTokenJob: Job? = null

    var faceApiEndpoint by mutableStateOf(
        sharedPreferences.getString(
            SharedPrefKeys.FACE_API_ENDPOINT, ""
        ) ?: ""
    )
        private set

    var faceApiKey by mutableStateOf(
        sharedPreferences.getString(SharedPrefKeys.FACE_API_KEY, "") ?: ""
    )
        private set

    var lastSessionApimReqId by mutableStateOf(
        sharedPreferences.getString(
            SharedPrefKeys.LAST_SESSION_APIM_REQ_ID, ""
        ) ?: ""
    )
        private set

    var deviceId by mutableLongStateOf(
        sharedPreferences.getLong(
            SharedPrefKeys.DEVICE_ID, 0
        )
    )

    var sendResultsToClient by mutableStateOf(
        sharedPreferences.getBoolean(
            SharedPrefKeys.SEND_RESULTS_TO_CLIENT, false
        )
    )
        private set
    var setImageInClient by mutableStateOf(
        sharedPreferences.getBoolean(
            SharedPrefKeys.SET_IMAGE_IN_CLIENT, false
        )
    )
        private set
    var passiveActive by mutableStateOf(
        sharedPreferences.getBoolean(
            SharedPrefKeys.PASSIVE_ACTIVE, false
        )
    )
        private set

    var isLoading by mutableStateOf(false)
        private set

    private val _isTokenReady = MutableStateFlow(FaceSessionToken.sessionToken.isNotBlank())
    val isTokenReady: StateFlow<Boolean> = _isTokenReady

    fun fetchFaceAPISessionToken(
        faceApiEndpoint: String,
        faceApiKey: String,
        verifyImageArray: ByteArray?,
        sendResultsToClient: Boolean,
        setImageInClient: Boolean,
        passiveActive: Boolean,
        deviceId: Long,
        onTokenFetched: (() -> Unit)? = null
    ) {
        fetchTokenJob?.cancel()

        onTokenFetched?.let {
            isLoading = true
        }

        fetchTokenJob = viewModelScope.launch(context = Dispatchers.IO) {
            val url: URL?
            var urlConnection: HttpsURLConnection? = null
            if (faceApiEndpoint.isNotBlank() && faceApiKey.isNotBlank()) {
                try {
                    url = if (verifyImageArray != null) {
                        URL("$faceApiEndpoint/face/v1.1-preview.1/detectLivenessWithVerify/singleModal/sessions")
                    } else {
                        URL("$faceApiEndpoint/face/v1.1-preview.1/detectLiveness/singleModal/sessions")
                    }
                    val livenessMode = if (passiveActive) "PassiveActive" else "Passive"
                    val tokenRequest = JSONObject(
                        mapOf(
                            "livenessOperationMode" to livenessMode,
                            "sendResultsToClient" to sendResultsToClient,
                            "deviceCorrelationId" to UUID(deviceId, deviceId)
                        )
                    ).toString()
                    val charset: Charset = Charset.forName("UTF-8")
                    urlConnection = url.openConnection() as HttpsURLConnection
                    urlConnection.doOutput = true
                    urlConnection.setChunkedStreamingMode(0)
                    urlConnection.useCaches = false
                    urlConnection.setRequestProperty("Ocp-Apim-Subscription-Key", faceApiKey)
                    if (verifyImageArray == null || setImageInClient == true) {
                        urlConnection.setRequestProperty(
                            "Content-Type", "application/json; charset=$charset"
                        )
                        val out: OutputStream = BufferedOutputStream(urlConnection.outputStream)
                        out.write(tokenRequest.toByteArray(charset))
                        out.flush()
                    } else {
                        val boundary: String = UUID.randomUUID().toString()
                        urlConnection.setRequestProperty(
                            "Content-Type", "multipart/form-data; boundary=$boundary"
                        )
                        val outputStream = BufferedOutputStream(urlConnection.outputStream)

                        PrintWriter(OutputStreamWriter(outputStream, charset), true).use { writer ->
                            writer.append("--$boundary").append(LINE_FEED)
                            writer.append("Content-Type: application/json; charset=$charset")
                                .append(LINE_FEED)
                            writer.append("Content-Disposition: form-data; name=Parameters")
                                .append(LINE_FEED)
                            writer.append(LINE_FEED).append(tokenRequest).append(LINE_FEED)

                            writer.append("--$boundary").append(LINE_FEED)
                            writer.append("Content-Disposition: form-data; name=VerifyImage; filename=VerifyImage")
                                .append(LINE_FEED)
                            writer.append("Content-Type: application/octet-stream")
                                .append(LINE_FEED)
                            writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED)
                            writer.append(LINE_FEED).flush()
                            outputStream.write(verifyImageArray, 0, verifyImageArray.size)
                            outputStream.flush()
                            writer.append(LINE_FEED).flush()
                            writer.append(LINE_FEED).flush()

                            writer.append("--$boundary--").append(LINE_FEED).flush()
                            outputStream.flush()
                        }
                    }
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

                    withContext(Dispatchers.Main) {
                        onTokenFetched?.invoke()
                    }

                    if (throwable == null) {
                        val jsonObject = JSONObject(jsonResponse)
                        FaceSessionToken.sessionToken = jsonObject.getString("authToken")
                        _isTokenReady.value = true
                        if(setImageInClient == true && verifyImageArray != null) {
                            FaceSessionToken.sessionSetInClientVerifyImage = verifyImageArray
                        }
                        else {
                            FaceSessionToken.sessionSetInClientVerifyImage = null
                        }
                    } else {
                        Log.d(
                            "Face API Session Create",
                            "Status: ${urlConnection.responseCode} ${urlConnection.responseMessage}"
                        )
                        Log.d("Face API Session Create", "Body: $jsonResponse")
                        throw throwable
                    }
                } catch (ex: Exception) {
                    Log.wtf("Face API Session Create", "Error: ${ex.message}")
                    ex.printStackTrace()
                } finally {
                    isLoading = false
                    urlConnection?.disconnect()
                }
            }
        }
    }

    fun updateVerifyImage(verifyImageArray: ByteArray, onTokenFetched: (() -> Unit)) {
        fetchFaceAPISessionToken(
            faceApiEndpoint = faceApiEndpoint,
            faceApiKey = faceApiKey,
            verifyImageArray = verifyImageArray,
            sendResultsToClient = sendResultsToClient,
            setImageInClient = setImageInClient,
            deviceId = deviceId,
            passiveActive = passiveActive,
            onTokenFetched = onTokenFetched
        )
    }

    companion object {
        private const val LINE_FEED = "\r\n"
    }
}