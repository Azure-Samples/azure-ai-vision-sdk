package com.example.FaceAnalyzerSample

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.lang.Exception
import javax.net.ssl.HttpsURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.UUID

object Utils {
    var mSessionToken: String = ""
    private const val LINE_FEED = "\r\n"

    @OptIn(ExperimentalStdlibApi::class)
    @SuppressLint("HardwareIds")
    fun getFaceAPISessionToken(faceApiEndpoint: String, faceApiKey: String, verifyImageArray: ByteArray?, sendResultsToClient: Boolean, contentResolver: ContentResolver): String = runBlocking {
        withContext(Dispatchers.IO) {
            val url: URL?
            var urlConnection: HttpsURLConnection? = null
            if (faceApiEndpoint.isNotBlank() && faceApiKey.isNotBlank()) {
                try {
                    url = if (verifyImageArray != null) {
                        URL("$faceApiEndpoint/face/v1.1-preview.1/detectLivenessWithVerify/singleModal/sessions")
                    } else {
                        URL("$faceApiEndpoint/face/v1.1-preview.1/detectLiveness/singleModal/sessions")
                    }

                    val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).hexToLong()

                    val tokenRequest = JSONObject(mapOf(
                        "livenessOperationMode" to "Passive",
                        "sendResultsToClient" to sendResultsToClient,
                        "deviceCorrelationId" to UUID(deviceId, deviceId)
                    )).toString()
                    val charset: Charset = Charset.forName("UTF-8")
                    urlConnection = url.openConnection() as HttpsURLConnection
                    urlConnection.doOutput = true
                    urlConnection.setChunkedStreamingMode(0)
                    urlConnection.useCaches = false
                    urlConnection.setRequestProperty("Ocp-Apim-Subscription-Key", faceApiKey)
                    if (verifyImageArray == null) {
                        urlConnection.setRequestProperty("Content-Type", "application/json; charset=$charset")
                        val out: OutputStream = BufferedOutputStream(urlConnection.outputStream)
                        out.write(tokenRequest.toByteArray(charset))
                        out.flush()
                    } else {
                        val boundary: String = UUID.randomUUID().toString()
                        urlConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                        val outputStream = BufferedOutputStream(urlConnection.outputStream)

                        PrintWriter(OutputStreamWriter(outputStream, charset), true).use { writer ->
                            writer.append("--$boundary").append(LINE_FEED)
                            writer.append("Content-Type: application/json; charset=$charset").append(LINE_FEED)
                            writer.append("Content-Disposition: form-data; name=Parameters").append(LINE_FEED)
                            writer.append(LINE_FEED).append(tokenRequest).append(LINE_FEED)

                            writer.append("--$boundary").append(LINE_FEED)
                            writer.append("Content-Disposition: form-data; name=VerifyImage; filename=VerifyImage").append(LINE_FEED)
                            writer.append("Content-Type: application/octet-stream").append(LINE_FEED)
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

                    if (throwable == null) {
                        val jsonObject = JSONObject(jsonResponse)
                        mSessionToken = jsonObject.getString("authToken")
                    } else {
                        Log.d("Face API Session Create", "Status: ${urlConnection.responseCode} ${urlConnection.responseMessage}")
                        Log.d("Face API Session Create", "Body: $jsonResponse")
                        throw throwable
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                } finally {
                    urlConnection!!.disconnect()
                }
            }
            return@withContext ""
        }
    }
}