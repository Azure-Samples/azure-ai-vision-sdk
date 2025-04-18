package com.rnazureliveness

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.azure.android.ai.vision.face.ui.FaceLivenessDetector

class LivenessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionToken = intent.getStringExtra("sessionToken") ?: ""
        val verifyImageFileContent = intent.getByteArrayExtra("verifyImageFileContent")
        val isLiveNess = intent.getStringExtra("isLiveNess") ?: ""
        setContent {
            FaceLivenessDetector(
                sessionAuthorizationToken = sessionToken,
                verifyImageFileContent = verifyImageFileContent,
                deviceCorrelationId = null,
                onSuccess = { result ->
                    val resultData = Intent().apply {
                        putExtra("status", "success")
                        putExtra("resultId", isLiveNess)
                        putExtra("digest", result.digest)
                        putExtra("data", result.toString())

                    }
                    setResult(Activity.RESULT_OK, resultData)
                    finish()
                },
                onError = { error ->
                    val errorData = Intent().apply {
                        putExtra("status", "error")
                        putExtra("livenessError", error.livenessError.toString())
                        putExtra("recognitionError", error.recognitionError.toString())
                        putExtra("data",  error.toString())
                    }
                    setResult(Activity.RESULT_OK, errorData)
                    finish()
                }
            )
        }
    }
}
