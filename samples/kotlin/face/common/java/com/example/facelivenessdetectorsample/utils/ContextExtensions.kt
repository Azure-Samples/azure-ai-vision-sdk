package com.microsoft.azure.ai.vision.facelivenessdetectorsample.utils

import android.content.Context
import android.net.Uri
import android.provider.Settings
import java.io.ByteArrayOutputStream
import java.io.IOException

fun Context.getVerifyImage(uri: Uri) : ByteArray {
    try {
        this.applicationContext.contentResolver.openInputStream(uri).use { inputStream ->
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

@OptIn(ExperimentalStdlibApi::class)
fun Context.getDeviceIdExt(): Long {
    return Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID).hexToLong()
}