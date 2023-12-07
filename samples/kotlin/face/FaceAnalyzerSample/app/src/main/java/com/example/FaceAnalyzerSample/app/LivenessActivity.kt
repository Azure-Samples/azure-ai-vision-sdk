package com.example.FaceAnalyzerSample

import AnalyzeModel
import AnalyzedResult
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.ResultReceiver
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat

class LivenessActivity : AppCompatActivity() {
    private var mAppPermissionGranted = false
    private lateinit var mStartButton: Button
    private lateinit var mImageView: ImageView
    private var mVerifyImagePath: String? = null
    private var mFaceApiEndpoint: String? = null
    private var mSessionToken: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_activity_liveness)
        mStartButton = findViewById(R.id.startButton)
        mStartButton.isEnabled = false
        mStartButton.setOnClickListener { startAnalyzeActivity() }
        mImageView = findViewById(R.id.imageView)
        val analyzeModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("model", AnalyzeModel::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra<AnalyzeModel>("model")
        }
        mVerifyImagePath = analyzeModel?.verifyImageURL
        mFaceApiEndpoint = analyzeModel?.endpoint
        mSessionToken = analyzeModel?.token
        if (!mVerifyImagePath.isNullOrBlank()) {
            val bitmapImage = BitmapFactory.decodeFile(mVerifyImagePath)
            val imageSz = 100.0
            if (bitmapImage.height < bitmapImage.width) {
                val nw = (bitmapImage.width * (imageSz / bitmapImage.height)).toInt()
                val scaled = Bitmap.createScaledBitmap(bitmapImage, nw, imageSz.toInt(), true)
                mImageView.setImageBitmap(scaled)
            } else {
                val nh = (bitmapImage.height * (imageSz / bitmapImage.width)).toInt()
                val scaled = Bitmap.createScaledBitmap(bitmapImage, imageSz.toInt(), nh, true)
                mImageView.setImageBitmap(scaled)
            }
        }
    }

    private fun startAnalyzeActivity() {
        val intent = Intent(this, AnalyzeActivity::class.java)
        val ctx = this.applicationContext
        val resultReceiver = object: ResultReceiver(null)
        {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if(resultCode == AnalyzedResultType.RESULT)
                {
                    if(resultData == null)
                    {
                        //error
                        return
                    }
                    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        resultData.getParcelable("result", AnalyzedResult::class.java)
                    } else {
                        @Suppress("DEPRECATION") resultData.getParcelable<AnalyzedResult>("result")
                    }

                    val livenessStatus = result?.livenessStatus.toString()
                    val livenessFailureReason = result?.failureReason.toString()
                    val verificationStatus = result?.verificationStatus.toString()
                    val verificationConfidence = result?.confidence.toString()
                    val digest = result?.digest
                    val resultIds = result?.resultId
                    val sharedPref = ctx.getSharedPreferences("SettingValues", Context.MODE_PRIVATE)
                    val resultIntent = Intent(ctx, ResultActivity::class.java).apply {
                        putExtra("livenessStatus", livenessStatus)
                        putExtra("livenessFailureReason", livenessFailureReason)
                        putExtra("verificationStatus", verificationStatus)
                        putExtra("verificationConfidence",verificationConfidence)
                        sharedPref.edit().putString("resultIds", resultIds).apply()
                    }

                    if(mVerifyImagePath.isNullOrBlank() == false)
                    {
                        val verifyImageFile = java.io.File(mVerifyImagePath.toString())
                        if(verifyImageFile.exists())
                        {
                            verifyImageFile.delete()
                        }
                    }
                    startActivity(resultIntent)
                }
                else if (resultCode == AnalyzedResultType.BACKPRESSED)
                {
                    val backintent = Intent(ctx, MainActivity::class.java)
                    backintent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(backintent)
                }
                else if(resultCode == AnalyzedResultType.ERROR)
                {
                    val resultIntent = Intent(ctx, ResultActivity::class.java).apply {
                        putExtra("error", "Missing or incorrect settings")
                    }
                    startActivity(resultIntent)
                }
            }
        }
        val analyzeModel = AnalyzeModel(mFaceApiEndpoint!!, mSessionToken!!, mVerifyImagePath, resultReceiver)
        intent.putExtra("model", analyzeModel);
        this.startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        if (isAccessGranted(Manifest.permission.CAMERA)) {
            mAppPermissionGranted = true
            mStartButton.isEnabled = true
        } else {
            Toast.makeText(
                this,
                "Cannot run application because permissions have not been granted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun isAccessGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            applicationContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

}