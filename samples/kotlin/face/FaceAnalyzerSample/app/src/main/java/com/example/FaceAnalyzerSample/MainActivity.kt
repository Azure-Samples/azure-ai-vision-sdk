//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

package com.example.FaceAnalyzerSample

import PickImage
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.ImageView
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
import java.io.InputStream

/***
 * Home page
 */
open class MainActivity : AppCompatActivity() {
    private lateinit var mLivenessButton: Button
    private lateinit var mLivenessVerifyButton: Button
    private lateinit var mSelectVerifyImageButton: Button
    private lateinit var mSelectedImageView: ImageView
    private val cAppRequestCode = 1
    private var mAppPermissionGranted = false
    private var mAppPermissionRequested = false
    private var mVerifyImage: ByteArray? = null
    private lateinit var mPickMedia: ActivityResultLauncher<PickVisualMediaRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_activity_main)
        mLivenessButton = findViewById(R.id.livenessButton)
        mLivenessButton.setOnClickListener { launchLiveness() }
        mLivenessVerifyButton = findViewById(R.id.livenessVerifybutton)
        mLivenessVerifyButton.setOnClickListener { launchLivenessVerify() }
        mSelectVerifyImageButton = findViewById(R.id.selectVerifyImage)
        mSelectVerifyImageButton.setOnClickListener { selectVerifyImage() }
        mSelectedImageView = findViewById(R.id.imageView)

        mPickMedia = registerForActivityResult(PickImage()) { uri ->
            if (uri != null) {
                mVerifyImage = AppUtils.getVerifyImage(this, uri)
                val orientation = this.applicationContext.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Images.ImageColumns.ORIENTATION),
                        MediaStore.Images.Media._ID + " = ?",
                        arrayOf(DocumentsContract.getDocumentId(uri).split(":")[1]),
                        null).use {
                    return@use if (it == null || it.count != 1 || !it.moveToFirst()) null else
                        when (it.getInt(0)) {
                            -270 -> ExifInterface.ORIENTATION_ROTATE_90
                            -180 -> ExifInterface.ORIENTATION_ROTATE_180
                            -90 -> ExifInterface.ORIENTATION_ROTATE_270
                            90 -> ExifInterface.ORIENTATION_ROTATE_90
                            180 -> ExifInterface.ORIENTATION_ROTATE_180
                            270 -> ExifInterface.ORIENTATION_ROTATE_270
                            else -> null
                        }
                }
                this.applicationContext.contentResolver.openInputStream(uri).use { inputStream ->
                    if (inputStream != null) {
                        showImage(inputStream, orientation)
                    }
                }
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
    @SuppressLint("NewApi")
    private fun showImage(inputStream: InputStream, knownOrientationExifEnum: Int?) {
        var bitmapImage =
            BitmapFactory.decodeStream(inputStream)

        try {   // rotate bitmap (best effort)
            val matrix = Matrix()
            (knownOrientationExifEnum ?: ExifInterface(inputStream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
                .let { orientation ->
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90F)
                        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180F)
                        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270F)
                        else -> return@let
                    }
                    bitmapImage = Bitmap.createBitmap(
                        bitmapImage,
                        0,
                        0,
                        bitmapImage.width,
                        bitmapImage.height,
                        matrix,
                        true
                    )
                }
        } catch (_: Throwable) {
        }

        val imageSz = 200.0
        bitmapImage = if (bitmapImage.height < bitmapImage.width) {
            val nw = (bitmapImage.width * (imageSz / bitmapImage.height)).toInt()
            Bitmap.createScaledBitmap(bitmapImage, nw, imageSz.toInt(), true)
        } else {
            val nh = (bitmapImage.height * (imageSz / bitmapImage.width)).toInt()
            Bitmap.createScaledBitmap(bitmapImage, imageSz.toInt(), nh, true)
        }
        mSelectedImageView.setImageBitmap(bitmapImage)
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
        AppUtils.getFaceAPISessionToken(this, null)
        val sharedPref = this.getSharedPreferences("SettingValues", Context.MODE_PRIVATE)
        val faceApiEndpoint = sharedPref.getString("endpoint", "").toString()
        val token = Utils.mSessionToken
        val model = AnalyzeModel(faceApiEndpoint, token, null)
        val intent = Intent(this, LivenessActivity::class.java)
        intent.putExtra("model", model);
        this.startActivity(intent)
    }

    fun launchLivenessVerify() {
        if (mVerifyImage != null) {
            val sharedPref = this.getSharedPreferences("SettingValues", Context.MODE_PRIVATE)
            val faceApiEndpoint = sharedPref.getString("endpoint", "").toString()
            AppUtils.getFaceAPISessionToken(this, mVerifyImage)
            val token = Utils.mSessionToken
            val model = AnalyzeModel(faceApiEndpoint, token, null)
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

    fun selectVerifyImage() {
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
