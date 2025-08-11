package com.microsoft.azure.ai.vision.facelivenessdetectorsample.viewmodel

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.token.FaceSessionToken
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.utils.SharedPrefKeys
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

    var userId by mutableStateOf(
        sharedPreferences.getString(SharedPrefKeys.USER_ID, null)?.let { 
            UUID.fromString(it) 
        } ?: UUID.randomUUID()
    )

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

    init {
        if (!sharedPreferences.contains(SharedPrefKeys.PASSIVE_ACTIVE)) {
            sharedPreferences.edit()
                .putBoolean(SharedPrefKeys.PASSIVE_ACTIVE, true)
                .apply()
            passiveActive = true
        }
    }

    fun fetchFaceAPISessionToken(
        faceApiEndpoint: String,
        faceApiKey: String,
        verifyImageArray: ByteArray?,
        setImageInClient: Boolean,
        passiveActive: Boolean,
        deviceId: Long,
        userId: UUID,
        onTokenFetched: (() -> Unit)? = null
    ) {
        fetchTokenJob?.cancel()

        onTokenFetched?.let {
            isLoading = true
        }

        fetchTokenJob = viewModelScope.launch(context = Dispatchers.IO) {
            var url: URL?
            var urlConnection: HttpsURLConnection? = null
            val type = if (verifyImageArray != null) "detectLivenessWithVerify" else "detectLiveness"
            var urlConnectionResponseCode: Int = 0
            if (faceApiEndpoint.isNotBlank() && faceApiKey.isNotBlank()) {
                try {
                    url = URL("$faceApiEndpoint/face/${FaceSessionToken.mFaceApiVersion}/$type-sessions")
                    val livenessMode = if (passiveActive) "PassiveActive" else "Passive"
                    val parameters =
                        mapOf(
                            "livenessOperationMode" to livenessMode,
                            "deviceCorrelationId" to UUID(deviceId, deviceId),
                            "userCorrelationId" to userId
                        )
                    val charset: Charset = Charset.forName("UTF-8")
                    urlConnection = url.openConnection() as HttpsURLConnection
                    urlConnection.setConnectTimeout(30000)
                    urlConnection.doOutput = true
                    urlConnection.setChunkedStreamingMode(0)
                    urlConnection.useCaches = false
                    urlConnection.setRequestProperty("Ocp-Apim-Subscription-Key", faceApiKey)
                    if (verifyImageArray == null) {
                        urlConnection.setRequestProperty(
                            "Content-Type", "application/json; charset=$charset"
                        )
                        val out: OutputStream = BufferedOutputStream(urlConnection.outputStream)
                        out.write(JSONObject(parameters).toString().toByteArray(charset))
                        out.flush()
                    } else {
                        val boundary: String = UUID.randomUUID().toString()
                        urlConnection.setRequestProperty(
                            "Content-Type", "multipart/form-data; boundary=$boundary"
                        )
                        val outputStream = BufferedOutputStream(urlConnection.outputStream)

                        PrintWriter(OutputStreamWriter(outputStream, charset), true).use { writer ->
                            for ((name, content) in parameters) {
                                writer.append("--$boundary").append(LINE_FEED)
                                writer.append("Content-Type: application/json; charset=$charset")
                                    .append(LINE_FEED)
                                writer.append("Content-Disposition: form-data; name=$name")
                                    .append(LINE_FEED)
                                writer.append(LINE_FEED).append(content.toString()).append(LINE_FEED)
                            }
                            if(setImageInClient == false){
                                writer.append("--$boundary").append(LINE_FEED)
                                writer.append("Content-Disposition: form-data; name=verifyImage; filename=VerifyImage")
                                    .append(LINE_FEED)
                                writer.append("Content-Type: application/octet-stream")
                                    .append(LINE_FEED)
                                writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED)
                                writer.append(LINE_FEED).flush()
                                outputStream.write(verifyImageArray, 0, verifyImageArray.size)
                                outputStream.flush()
                                writer.append(LINE_FEED).flush()
                            }

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
                        FaceSessionToken.sessionId = jsonObject.getString("sessionId")
                        if(verifyImageArray != null){
                            FaceSessionToken.isVerifyImage = true
                        } else {
                            FaceSessionToken.isVerifyImage = false
                        }
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
                        val requestId: String? = urlConnection.getHeaderField("apim-request-id")
                        requestId?.also {
                            Log.d("Face API Session Create", "apim-request-id: $this")
                        }
                        Log.d("Face API Session Create", "Body: $jsonResponse")
                        throw throwable
                    }
                } catch (ex: Exception) {
                    Log.wtf("Face API Session Create", "Error: ${ex.message}")
                    ex.printStackTrace()
                } finally {
                    isLoading = false
                    try {
                        urlConnectionResponseCode = urlConnection?.responseCode ?: 0
                    } catch (ex: Exception) {
                        Log.wtf("Face API Session Create Cleanup", "Error: ${ex.message}")
                        ex.printStackTrace()
                    } finally {
                        urlConnection?.disconnect()
                    }
                }
            }
        }
    }

    fun updateVerifyImage(verifyImageArray: ByteArray, onTokenFetched: (() -> Unit)) {
        fetchFaceAPISessionToken(
            faceApiEndpoint = faceApiEndpoint,
            faceApiKey = faceApiKey,
            verifyImageArray = verifyImageArray,
            setImageInClient = setImageInClient,
            deviceId = deviceId,
            userId = userId,
            passiveActive = passiveActive,
            onTokenFetched = onTokenFetched
        )
    }

    companion object {
        private const val LINE_FEED = "\r\n"
    }
}