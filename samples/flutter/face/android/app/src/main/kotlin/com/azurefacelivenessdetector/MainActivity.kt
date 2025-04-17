package com.azurefacelivenessdetector

import android.content.Intent
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel


class MainActivity : FlutterActivity() {
    private val CHANNEL = "azure_face_liveness_channel"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler {
                call, result ->
            if (call.method == "startLiveness") {
                val sessionToken = call.argument<String>("sessionToken")
                val verifyImage = call.argument<ByteArray>("verifyImageFileContent")

                val intent = Intent(this, LivenessActivity::class.java)
                intent.putExtra("sessionToken", sessionToken)
                intent.putExtra("verifyImageFileContent", verifyImage)

                startActivityForResult(intent, 101)

                activityResultCallback = result // Save callback
            } else {
                result.notImplemented()
            }
        }
    }

    private var activityResultCallback: MethodChannel.Result? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 101 && activityResultCallback != null) {
            val status = data?.getStringExtra("status")
            if (status == "success") {
                val resultMap = hashMapOf<String, Any?>(
                    "status" to "success",
                    "resultId" to data.getStringExtra("resultId"),
                    "digest" to data.getStringExtra("digest"),
                    "data" to data.getStringExtra("data")
                )
                activityResultCallback?.success(resultMap)
            } else if (status == "error") {
                val errorMap = hashMapOf<String, Any?>(
                    "status" to "error",
                    "livenessError" to data.getStringExtra("livenessError"),
                    "recognitionError" to data.getStringExtra("recognitionError"),
                    "data" to data.getStringExtra("data")
                )
                activityResultCallback?.success(errorMap)
            } else {
                activityResultCallback?.error("UNEXPECTED_RESULT", "No valid result returned", null)
            }
            activityResultCallback = null

        }
    }
}
