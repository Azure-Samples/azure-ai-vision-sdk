//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.UUID
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.FaceAnalyzerSample.app.Utils
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
object AppUtils {
    // Function to get a session token for face liveness session
    fun GetFaceAPISessionToken(context: Context, isVerify: Boolean ) {
        val sharedPref = context.getSharedPreferences("SettingValues", Context.MODE_PRIVATE)
        val faceApiEndpoint = sharedPref.getString("endpoint", "").toString()
        val faceApiKey = sharedPref.getString("key", "").toString()
        Utils.GetFaceAPISessionToken(faceApiEndpoint, faceApiKey, isVerify)
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