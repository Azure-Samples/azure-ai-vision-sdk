package com.microsoft.azure.ai.vision.facelivenessdetectorsample

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.navigation.AppNavHost
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.ui.theme.FaceLivenessDetectorSampleTheme


class MainActivity : ComponentActivity() {
    val cAppRequestCode = 1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        setContent {
            val navController = rememberNavController()
            FaceLivenessDetectorSampleTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost(navController)
                }
            }
        }
    }

    override fun attachBaseContext(base: android.content.Context?) {
        super.attachBaseContext(base)
        com.google.android.play.core.splitcompat.SplitCompat.install(this)
        com.google.android.play.core.splitcompat.SplitCompat.installActivity(this)
    }

    /**
     * Requests camera and storage permissions needed by application
     */
    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                cAppRequestCode
            )
        }
    }

    /**
     * Handles permission results.
     * If all permissions are granted, mAppPermissionGranted is set to true
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            cAppRequestCode -> {
                for (grantResult in grantResults) {
                    if (grantResult == PackageManager.PERMISSION_DENIED) {
                        return
                    }
                }
            }
        }
    }
}
