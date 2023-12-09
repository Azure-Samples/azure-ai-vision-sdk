//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

package com.example.FaceAnalyzerSample

import AnalyzeModel
import PickImage
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.FaceAnalyzerSample.app.Utils

/***
 * Home page
 */
class MainActivity : AppCompatActivity() {
    private lateinit var mLivenessButton: Button
    private lateinit var mLivenessVerifyButton: Button
    private val cAppRequestCode = 1
    private var mAppPermissionGranted = false
    private var mAppPermissionRequested = false
    private lateinit var mPickMedia: ActivityResultLauncher<PickVisualMediaRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_activity_main)
        mLivenessButton = findViewById(R.id.livenessButton)
        mLivenessButton.setOnClickListener { launchLiveness() }
        mLivenessVerifyButton = findViewById(R.id.livenessVerifybutton)
        mLivenessVerifyButton.setOnClickListener { launchLivenessVerify() }

        mPickMedia = registerForActivityResult(PickImage()) { uri ->
            if (uri != null) {
                val sharedPref = this.getSharedPreferences("SettingValues", Context.MODE_PRIVATE)
                val faceApiEndpoint = sharedPref.getString("endpoint", "").toString()
                val token = Utils.mSessionToken
                val verifyFileURL = AppUtils.GetVerifyImage(this, uri)
                val model = AnalyzeModel(faceApiEndpoint, token, verifyFileURL, null)
                val intent = Intent(this, LivenessActivity::class.java)
                intent.putExtra("model", model);
                this.startActivity(intent)
            }
            else {
                val intent = Intent(this, ResultActivity::class.java).apply {
                    putExtra("error", "Missing verification image")
                }
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestPermissions()

        if (!isOnline(this)) {
            mLivenessButton.isEnabled = false
            mLivenessVerifyButton.isEnabled = false

            Toast.makeText(this, "No network connection", Toast.LENGTH_SHORT).show()
        } else {
            mLivenessButton.isEnabled = true
            mLivenessVerifyButton.isEnabled = true
        }
    }

    fun launchLiveness() {
        AppUtils.GetFaceAPISessionToken(this, false)
        val sharedPref = this.getSharedPreferences("SettingValues", Context.MODE_PRIVATE)
        val faceApiEndpoint = sharedPref.getString("endpoint", "").toString()
        val token = Utils.mSessionToken
        val model = AnalyzeModel(faceApiEndpoint, token, "", null)
        val intent = Intent(this, LivenessActivity::class.java)
        intent.putExtra("model", model);
        this.startActivity(intent)
    }

    fun launchLivenessVerify() {
        AppUtils.GetFaceAPISessionToken(this, true)
        mPickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.SingleMimeType("*/*")))
    }

    fun onSettingsClick(@Suppress("UNUSED_PARAMETER") view: View?) {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
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
                        mAppPermissionGranted = false
                        return
                    }
                }
                mAppPermissionGranted = true
            }
        }
    }

    /**
     * Requests camera and storage permissions needed by application
     */
    private fun requestPermissions() {
        var permissions: ArrayList<String> = ArrayList()

        if (!mAppPermissionGranted && !mAppPermissionRequested) {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.CAMERA)
            }
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            var perms = permissions.toTypedArray()

            if (permissions.size > 1) {
                ActivityCompat.requestPermissions(
                    this,
                    perms,
                    cAppRequestCode
                )
            }
        }
    }

    /**
     * Check if device has network connection
     */
    private fun isOnline(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                else -> false
            }
        } else {
            // if the android version is below M
            @Suppress("DEPRECATION") val networkInfo =
                connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }
}
