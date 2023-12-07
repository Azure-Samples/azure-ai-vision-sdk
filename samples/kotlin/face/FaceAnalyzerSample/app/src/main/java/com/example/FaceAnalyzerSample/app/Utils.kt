package com.example.FaceAnalyzerSample.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.UUID

object Utils {
    var mSessionToken: String = ""
    fun GetFaceAPISessionToken(faceApiEndpoint: String, faceApiKey: String, isVerify: Boolean)= runBlocking{
        withContext(Dispatchers.IO) {
            var url: URL?
            var urlConnection: HttpURLConnection? = null
            if (!faceApiEndpoint.isNullOrBlank() && !faceApiKey.isNullOrBlank()) {
                try {
                    if (isVerify) {
                        url =
                            URL(faceApiEndpoint + "/face/v1.1-preview.1/detectLivenessWithVerify/singleModal/sessions")
                    } else {
                        url =
                            URL(faceApiEndpoint + "/face/v1.1-preview.1/detectLiveness/singleModal/sessions")
                    }

                    urlConnection = url.openConnection() as HttpURLConnection
                    urlConnection.doOutput = true
                    urlConnection.setChunkedStreamingMode(0)
                    urlConnection.useCaches = false
                    urlConnection.setRequestProperty("Ocp-Apim-Subscription-Key", "$faceApiKey")
                    urlConnection.setRequestProperty("Content-Type", "application/json")
                    val out: OutputStream = BufferedOutputStream(urlConnection.outputStream)
                    val tokenRequest =
                        "{\"livenessOperationMode\":\"Passive\",\"deviceCorrelationId\":\"" + UUID.randomUUID()
                            .toString() + "\"}"
                    out.write(tokenRequest.toByteArray(Charset.forName("UTF-8")))
                    out.flush()
                    val reader = BufferedReader(InputStreamReader(urlConnection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    // Parse the JSON response
                    val jsonResponse = response.toString()
                    val jsonObject = JSONObject(jsonResponse)
                    mSessionToken = jsonObject.getString("authToken")
                } catch (ex: Exception) {
                    ex.printStackTrace()
                } finally {
                    urlConnection!!.disconnect()
                }
            }
        }
    }
}