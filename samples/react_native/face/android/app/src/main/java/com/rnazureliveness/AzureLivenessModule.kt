package com.rnazureliveness

import android.content.Intent
import android.util.Log
import com.facebook.react.bridge.*
import java.io.File


class AzureLivenessModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "AzureLiveness"

    @ReactMethod
    fun startLiveness(token: String, verifyImage: String, isLiveNess: String) {
        val intent = Intent(currentActivity, LivenessActivity::class.java)
        intent.putExtra("sessionToken", token)
        intent.putExtra("isLiveNess", isLiveNess)
        if (!verifyImage.isNullOrBlank()) {
            val byteArray = File(verifyImage).readBytes()
            intent.putExtra("verifyImageFileContent", byteArray)
        } else {
            intent.putExtra("verifyImageFileContent", verifyImage)
        }
        currentActivity?.startActivityForResult(intent, 1110)
    }

}
