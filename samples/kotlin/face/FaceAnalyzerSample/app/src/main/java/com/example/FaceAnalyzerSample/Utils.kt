//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.lang.Exception
import java.net.HttpURLConnection
import java.nio.charset.Charset
import java.net.URL
import java.util.UUID
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PickImage : ActivityResultContracts.PickVisualMedia() {
    override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent {
        val intent = super.createIntent(context, input)
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png"))
        return intent
    }
}

object Utils {
    // Function to get a session token for face liveness session
    fun GetFaceAPISessionToken(context: Context, isVerify: Boolean ) = runBlocking {
        withContext(Dispatchers.IO) {
            val sharedPref = context.getSharedPreferences("SettingValues", Context.MODE_PRIVATE)
            val faceApiEndpoint = sharedPref.getString("endpoint", "").toString()
            val faceApiKey = sharedPref.getString("key", "").toString()
            val sendResultsToClient = sharedPref.getBoolean("sendResultsToClient", false)

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
                        "{\"livenessOperationMode\":\"Passive\", \"sendResultsToClient\":\"" + "$sendResultsToClient" + "\", \"deviceCorrelationId\":\"" + UUID.randomUUID()
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
                    val jsonObject = org.json.JSONObject(jsonResponse)
                    sharedPref.edit().putString("token", jsonObject.getString("authToken")).apply()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                } finally {
                    urlConnection!!.disconnect()
                }
            }
        }
    }

    // Function to retrieve a string value from SharedPreferences
    fun GetVerifyImage(context: Context, uri: Uri) : String {
        val filetype = context.applicationContext.getContentResolver().getType(uri)!!.split('/')[1]
        val file: File = File(
            context.applicationContext.getCacheDir(), UUID.randomUUID().toString() + "." + filetype)
        try {
            context.applicationContext.getContentResolver().openInputStream(uri).use { inputStream ->
                FileOutputStream(file).use { output ->
                    inputStream!!.copyTo(output)
                    output.flush()
                }
            }
        } catch (ex: IOException) {
            ex.printStackTrace()
        }

        return file.path
    }
}