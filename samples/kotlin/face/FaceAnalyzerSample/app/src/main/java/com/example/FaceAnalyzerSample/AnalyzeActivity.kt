//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

package com.example.FaceAnalyzerSample

import AnalyzeModel
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.LifecycleOwner
import com.azure.ai.vision.common.internal.implementation.EventListener
import com.azure.android.ai.vision.common.VisionServiceOptions
import com.azure.android.ai.vision.common.VisionSource
import com.azure.android.ai.vision.common.VisionSourceOptions
import com.azure.android.ai.vision.faceanalyzer.*
import com.azure.android.core.credential.AccessToken
import com.azure.android.core.credential.TokenCredential
import com.azure.android.core.credential.TokenRequestContext
import org.threeten.bp.OffsetDateTime
import java.io.File
import java.net.URL
import kotlin.math.sqrt

/***
 * Sample class to fetch token for starting liveness session.
 * It is recommended to fetch this token from app server for production as part of init section.
 */
class StringTokenCredential(token: String) : TokenCredential {
    override fun getToken(
        request: TokenRequestContext,
        callback: TokenCredential.TokenCredentialCallback
    ) {
        callback.onSuccess(_token)
    }

    private var _token: AccessToken? = null

    init {
        _token = AccessToken(token, OffsetDateTime.MAX)
    }
}

/***
 * Analyze activity performs one-time face analysis, using the default camera stream as input.
 * Launches the result activity once the analyzed event is triggered.
 */
class AnalyzeActivity : AppCompatActivity() {
    private var mAppPermissionGranted = false
    private lateinit var mStartButton: Button
    private lateinit var mImageView: ImageView
    private lateinit var mSurfaceView: SurfaceView
    private lateinit var mCameraPreviewLayout: FrameLayout
    private lateinit var mBackgroundLayout: ConstraintLayout
    private lateinit var mInstructionsView: TextView
    private val cPreviewAreaRatio = 0.12
    private var mVisionSource: VisionSource? = null
    private var mFaceAnalyzer: FaceAnalyzer? = null
    private var mFaceAnalysisOptions: FaceAnalysisOptions? = null
    private var mServiceOptions: VisionServiceOptions? = null
    private var mFaceApiEndpoint: String? = null
    private var mBackPressed: Boolean = false
    private var mResultIds: ArrayList<String> = ArrayList()
    private var mHandler = Handler(Looper.getMainLooper()) // Handler for posting delayed text updates
    private var lastTextUpdateTime = 0L // Keep track of the last text update time
    private val delayMillis = 200L // Define the throttle delay
    private var mDoneAnalyzing: Boolean = false
    private var mVerifyImagePath: String? = null
    private var mSessionToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_analyze)
        mStartButton = findViewById(R.id.startButton)
        mStartButton.isEnabled = false
        mStartButton.setOnClickListener { startAnalyzeOnce() }
        mSurfaceView = AutoFitSurfaceView(this)
        mCameraPreviewLayout = findViewById(R.id.camera_preview)
        mCameraPreviewLayout.removeAllViews()
        mCameraPreviewLayout.addView(mSurfaceView)
        mCameraPreviewLayout.visibility = View.INVISIBLE
        mInstructionsView = findViewById(R.id.instructionString);
        mBackgroundLayout = findViewById(R.id.activity_main_layout)
        mImageView = findViewById(R.id.imageView)
        var analyzeModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("model", AnalyzeModel::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra<AnalyzeModel>("model")
        }

        mVerifyImagePath = analyzeModel?.verifyImageURL
        mFaceApiEndpoint = analyzeModel?.endpoint
        mSessionToken = analyzeModel?.token
        if (mFaceApiEndpoint.isNullOrBlank() || mSessionToken.isNullOrBlank()) {
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra("error", "Missing or Incorrect settings")
            }
            startActivity(intent)
            return
        }

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

    override fun onResume() {
        super.onResume()
        initializeConfig()
        checkPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        mVisionSource?.close()
        mVisionSource = null
        mServiceOptions?.close()
        mServiceOptions = null
        mFaceAnalysisOptions?.close()
        mFaceAnalysisOptions = null
        try {
            mFaceAnalyzer?.close()
            mFaceAnalyzer = null
        } catch (ex: Exception) {
            Log.println(Log.ERROR, "FACE_TELEMETRY", "Cannot dispose face analyzer")
            ex.printStackTrace()
        }
    }

    /**
     * Creates the VisionServiceOptions using the FaceApi endpoint and token supplied
     */
    private fun initializeConfig() {
        if (!mFaceApiEndpoint.isNullOrBlank()) {
            mServiceOptions = VisionServiceOptions(URL(mFaceApiEndpoint))
            mServiceOptions?.setTokenCredential(StringTokenCredential(mSessionToken.toString()))
        }
    }

    /**
     * Initializes vision source from camera stream and FaceAnalyzer
     */
    private fun onCameraPermitted() {
        if (mAppPermissionGranted) {
            mStartButton.isEnabled = true
            val visionSourceOptions = VisionSourceOptions(this, this as LifecycleOwner)
            visionSourceOptions.setPreview(mSurfaceView)
            mVisionSource = VisionSource.fromDefaultCamera(visionSourceOptions)
            displayCameraOnLayout()

            // Initialize faceAnalyzer with default camera as vision source
            createFaceAnalyzer()
        }
    }

    /**
     * Initializes FaceAnalyzer
     */
    private fun createFaceAnalyzer() {
        // Add create options
        FaceAnalyzerCreateOptions().use { createOptions ->

            // Sets how the face analysis is performed.
            // Assume there is temporal context in each subsequent image in the image-stream.
            createOptions.setFaceAnalyzerMode(FaceAnalyzerMode.TRACK_FACES_ACROSS_IMAGE_STREAM)

            mFaceAnalyzer = FaceAnalyzerBuilder()
                .serviceOptions(mServiceOptions)
                .source(mVisionSource)
                .createOptions(createOptions)
                .build().get()
        }

        mFaceAnalyzer?.apply {
            this.analyzed.addEventListener(analyzedListener)
            this.analyzing.addEventListener(analyzinglistener)
        }
    }

    /**
     * Listener for Analyzing callback. Receives tracking and user feedback information
     */
    private var analyzinglistener =
        EventListener<FaceAnalyzingEventArgs> { _, e ->

            e.result.use { result ->
                if (result.faces.isNotEmpty()) {
                    // Get the first face in result
                    var face = result.faces.iterator().next()

                    // Lighten/darken the screen based on liveness feedback
                    var requiredAction = face.actionRequiredFromApplicationTask?.action;
                    if (requiredAction == ActionRequiredFromApplication.BRIGHTEN_DISPLAY) {
                        mBackgroundLayout.setBackgroundColor(Color.WHITE)
                        face.actionRequiredFromApplicationTask.setAsCompleted()
                    } else if (requiredAction == ActionRequiredFromApplication.DARKEN_DISPLAY) {
                        mBackgroundLayout.setBackgroundColor(Color.BLACK)
                        face.actionRequiredFromApplicationTask.setAsCompleted()
                    }

                    // Display user feedback and warnings on UI
                    if (!mDoneAnalyzing) {
                        var feedbackMessage = MapFeedbackToMessage(FeedbackForFace.NONE)
                        if (face.feedbackForFace != null) {
                            feedbackMessage = MapFeedbackToMessage(face.feedbackForFace)
                        }

                        val currentTime = System.currentTimeMillis()
                        // Check if enough time has passed since the last update
                        if (currentTime - lastTextUpdateTime >= delayMillis) {
                            // Update the text view
                            updateTextView(feedbackMessage)

                            // Update the last update time
                            lastTextUpdateTime = currentTime
                        }
                    }
                }
            }
        }

    /**
     * Listener for Analyzed callback.
     * Receives recognition and liveness result.
     * Launches result activity.
     */
    private var analyzedListener =
        EventListener<FaceAnalyzedEventArgs> { _, e ->
            val intent = Intent(this, ResultActivity::class.java)
            e.result.use { result ->
                if (result.faces.isNotEmpty()) {
                    // Get the first face in result
                    var face = result.faces.iterator().next()
                    var livenessStatus = face.livenessResult?.livenessStatus?.toString()
                    var livenessFailureReason = face.livenessResult?.livenessFailureReason?.toString()
                    var verifyStatus = face.recognitionResult?.recognitionStatus?.toString()
                    var verifyConfidence = face.recognitionResult?.confidence.toString()
                    mResultIds.clear()
                    if (face.livenessResult.resultId != null) {
                        mResultIds.add(face.livenessResult.resultId.toString())
                    }
                    val sharedPref = this.getSharedPreferences("SettingValues", Context.MODE_PRIVATE)
                    intent.apply {
                        putExtra("livenessStatus", livenessStatus)
                        putExtra("livenessFailureReason", livenessFailureReason)
                        putExtra("verificationStatus", verifyStatus)
                        putExtra("verificationConfidence", verifyConfidence)
                        val resultIds = mResultIds.joinToString(",")
                        sharedPref.edit().putString("resultIds", resultIds).apply()
                    }
                } else {
                    intent.apply { putExtra("error", "Not computed") }
                }
                if(mVerifyImagePath != null && mVerifyImagePath != "")
                {
                    val verifyImageFile = File(mVerifyImagePath.toString())
                    if(verifyImageFile.exists())
                    {
                        verifyImageFile.delete()
                    }
                }
            }

            synchronized(this) {
                if (!mBackPressed) {
                    startActivity(intent)
                }
            }
        }

    /**
     * Sets faceAnalysisOptions and recognitionMode. Calls analyzeOnce.
     */
    private fun startAnalyzeOnce() {

        mCameraPreviewLayout.visibility = View.VISIBLE
        // Create faceAnalysisOptions for analyzeOnce

        /**
         * Check if any settings are missing.
         * Service Config can only be null if middle-tier mode is on.
         * If running recognition, the recognition mode must be set.
         */
        if ( mServiceOptions == null ) {

            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra("error", "Missing settings")
            }
            startActivity(intent)
            return
        }

        mFaceAnalysisOptions = FaceAnalysisOptions();

        mFaceAnalysisOptions?.setFaceSelectionMode(FaceSelectionMode.LARGEST)
        
        if(!mVerifyImagePath.isNullOrBlank())
        {
            val singleImageVs = VisionSource.fromFile(mVerifyImagePath)
            mFaceAnalysisOptions?.setRecognitionMode(RecognitionMode.valueOfVerifyingMatchToFaceInSingleFaceImage(singleImageVs))
        }

        try {
            mFaceAnalyzer?.analyzeOnceAsync(mFaceAnalysisOptions);
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        mStartButton.visibility = View.GONE
        mImageView.visibility = View.GONE
        mDoneAnalyzing = false
    }

    private fun updateTextView(newText: String) {
        mHandler.post {
            mInstructionsView.text = newText
        }
    }

    /***
     * Displays camera stream on UI in a circular shape.
     */
    private fun displayCameraOnLayout() {
        val previewSize = mVisionSource?.getCameraPreviewFormat()
        val params = mCameraPreviewLayout.layoutParams as ConstraintLayout.LayoutParams
        params.dimensionRatio = previewSize?.height.toString() + ":" + previewSize?.width
        params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        params.matchConstraintDefaultWidth = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_PERCENT
        params.matchConstraintPercentWidth =
            sqrt(4 * cPreviewAreaRatio * Resources.getSystem().displayMetrics.heightPixels / Math.PI / Resources.getSystem().displayMetrics.widthPixels).toFloat()
        mCameraPreviewLayout.layoutParams = params
    }

    /**
     * Override back button to always return to main activity
     */
    override fun onBackPressed() {
        synchronized(this) {
            mBackPressed = true
        }
        @Suppress("DEPRECATION") super.onBackPressed()
        mFaceAnalyzer?.stopAnalyzeOnce();
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    private fun checkPermissions() {
        if (isAccessGranted(Manifest.permission.CAMERA)) {
            mAppPermissionGranted = true
            onCameraPermitted()
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

    private fun MapFeedbackToMessage(feedback : FeedbackForFace): String {
        when(feedback) {
            FeedbackForFace.NONE -> return "Hold Still."
            FeedbackForFace.LOOK_AT_CAMERA -> return "Look at camera."
            FeedbackForFace.FACE_NOT_CENTERED -> return "Look at camera."
            FeedbackForFace.MOVE_CLOSER -> return "Too far away! Move in closer."
            FeedbackForFace.MOVE_BACK -> return "Too close! Move farther away."
            FeedbackForFace.REDUCE_MOVEMENT -> return "Too much movement."
            FeedbackForFace.SMILE -> return "Ready, set, smile!"
            FeedbackForFace.ATTENTION_NOT_NEEDED -> {
                mDoneAnalyzing = true
                return "Done, finishing up..."
            }
        }
    }
}
