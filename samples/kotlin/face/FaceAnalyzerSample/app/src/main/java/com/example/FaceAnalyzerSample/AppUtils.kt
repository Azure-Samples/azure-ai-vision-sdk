//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.FaceAnalyzerSample.Utils
import java.io.IOException
import java.io.ByteArrayOutputStream

class PickImage : ActivityResultContracts.PickVisualMedia() {
    override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent {
        val intent = super.createIntent(context, input)
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png"))
        return intent
    }
}
object AppUtils {
    // Function to get a session token for face liveness session
    fun getFaceAPISessionToken(context: Context, verifyImage: ByteArray? ) {
        val sharedPref = context.getSharedPreferences("SettingValues", Context.MODE_PRIVATE)
        val faceApiEndpoint = sharedPref.getString("endpoint", "").toString()
        val faceApiKey = sharedPref.getString("key", "").toString()
        val sendResultsToClient = sharedPref.getBoolean("sendResultsToClient", false)

        Utils.getFaceAPISessionToken(faceApiEndpoint, faceApiKey, verifyImage, sendResultsToClient, context.contentResolver)
    }
    // Function to retrieve a string value from SharedPreferences
    fun getVerifyImage(context: Context, uri: Uri) : ByteArray {
        try {
            context.applicationContext.contentResolver.openInputStream(uri).use { inputStream ->
                ByteArrayOutputStream().use { output ->
                    inputStream!!.copyTo(output)
                    output.flush()
                    return output.toByteArray()
                }

            }
        } catch (ex: IOException) {
            ex.printStackTrace()
            throw ex
        }
    }
}