//
// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
//

package com.example.azureaivisionfaceuiwrapper

import android.annotation.SuppressLint
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.azure.android.ai.vision.face.ui.FaceLivenessDetector

@SuppressLint("RestrictedApi")
open class FaceLivenessDetectorWrapper : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionAuthorizationToken = intent.getStringExtra("com.example.azureaivisionfaceuiwrapper.sessionAuthorizationToken") ?: ""
        val sessionId = intent.getStringExtra("com.example.azureaivisionfaceuiwrapper.sessionId") ?: ""
        Log.w("FaceLivenessDetectorWrapper", "token: $sessionAuthorizationToken")

        setContentView(
            ComposeView(this).apply {
                setContent {
                    FaceLivenessDetector(
                        sessionAuthorizationToken = sessionAuthorizationToken,
                        verifyImageFileContent = null,
                        deviceCorrelationId = null,
                        userCorrelationId = null,
                        onSuccess = { success ->
                            val resultIntent = Intent(ACTION_VIEW)
                            resultIntent.addCategory("com.example.azureaivisionfaceuiwrapper.result")
                            resultIntent.putExtra("com.example.azureaivisionfaceuiwrapper.result.sessionId", sessionId)
                            resultIntent.putExtra("com.example.azureaivisionfaceuiwrapper.result.type", "success")
                            resultIntent.putExtra("com.example.azureaivisionfaceuiwrapper.result.resultId", success.resultId)
                            resultIntent.putExtra("com.example.azureaivisionfaceuiwrapper.result.digest", success.digest)
                            if (resultIntent.resolveActivity(packageManager) != null) {
                                startActivity(resultIntent)
                            }
                        },
                        onError = { error ->
                            val resultIntent = Intent(ACTION_VIEW)
                            resultIntent.addCategory("com.example.azureaivisionfaceuiwrapper.result")
                            resultIntent.putExtra("com.example.azureaivisionfaceuiwrapper.result.sessionId", sessionId)
                            resultIntent.putExtra("com.example.azureaivisionfaceuiwrapper.result.type", "error")
                            resultIntent.putExtra("com.example.azureaivisionfaceuiwrapper.result.livenessError", error.livenessError.toString())
                            resultIntent.putExtra("com.example.azureaivisionfaceuiwrapper.result.recognitionError", error.recognitionError.toString())
                            if (resultIntent.resolveActivity(packageManager) != null) {
                                startActivity(resultIntent)
                            }
                        }
                    )
                }
            }
        )
    }
}