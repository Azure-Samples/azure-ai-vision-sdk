//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

package com.example.FaceAnalyzerSample

import android.content.res.Resources
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
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
import kotlin.math.sqrt

/***
 * Analyze activity performs one-time face analysis, using the default camera stream as input.
 * Launches the result activity once the analyzed event is triggered.
 */
open class AnalyzeActivity : AppCompatActivity() {
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

    private lateinit var mSurfaceView: SurfaceView
    private lateinit var mCameraPreviewLayout: FrameLayout
    private lateinit var mBackgroundLayout: ConstraintLayout
    private lateinit var mInstructionsView: TextView
    private val cPreviewAreaRatio = 0.12
    private var lastTextUpdateTime = 0L // Keep track of the last text update time
    private val delayMillis = 200L // Define the throttle delay
    private var mVisionSource: VisionSource? = null
    private var mFaceAnalyzer: FaceAnalyzer? = null
    private var mFaceAnalysisOptions: FaceAnalysisOptions? = null
    private var mServiceOptions: VisionServiceOptions? = null
    private var mFaceApiEndpoint: String? = null
    private var mSessionToken: String? = null
    private var mResultReceiver: ResultReceiver? = null
    private var mBackPressed: Boolean = false
    private var mHandler = Handler(Looper.getMainLooper()) // Handler for posting delayed text updates
    private var mDoneAnalyzing: Boolean = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_analyze)
        mSurfaceView = AutoFitSurfaceView(this)
        mCameraPreviewLayout = findViewById(R.id.camera_preview)
        mCameraPreviewLayout.removeAllViews()
        mCameraPreviewLayout.addView(mSurfaceView)
        mCameraPreviewLayout.visibility = View.INVISIBLE
        mInstructionsView = findViewById(R.id.instructionString)
        mBackgroundLayout = findViewById(R.id.activity_main_layout)
        val analyzeModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("model", AnalyzeModel::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra("model")
        }
        mFaceApiEndpoint = analyzeModel?.endpoint
        mSessionToken = analyzeModel?.token
        mResultReceiver = analyzeModel?.resultReceiver
        if (mFaceApiEndpoint.isNullOrBlank() || mSessionToken.isNullOrBlank()) {
            mResultReceiver?.send(AnalyzedResultType.ERROR, null)
            return
        }
    }

    override fun onResume() {
        super.onResume()
        if (mFaceAnalyzer == null) {
            initializeConfig()
            val visionSourceOptions = VisionSourceOptions(this, this as LifecycleOwner)
            visionSourceOptions.setPreview(mSurfaceView)
            mVisionSource = VisionSource.fromDefaultCamera(visionSourceOptions)
            displayCameraOnLayout()

            // Initialize faceAnalyzer with default camera as vision source
            createFaceAnalyzer()
        }
        startAnalyzeOnce()
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
            ex.printStackTrace()
        }
    }

    /**
     * Creates the VisionServiceOptions using the FaceApi endpoint and token supplied
     */
    private fun initializeConfig() {
        if (!mFaceApiEndpoint.isNullOrBlank()) {
            mServiceOptions = VisionServiceOptions(StringTokenCredential(mSessionToken.toString()))
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
            this.analyzing.addEventListener(analyzingListener)
            this.stopped.addEventListener(stoppedListener)
        }
    }

    /**
     * Listener for Analyzing callback. Receives tracking and user feedback information
     */
    @Suppress("MemberVisibilityCanBePrivate")
    protected var analyzingListener =
        EventListener<FaceAnalyzingEventArgs> { _, e ->

            e.result.use { result ->
                if (result.faces.isNotEmpty()) {
                    // Get the first face in result
                    val face = result.faces.iterator().next()

                    // Lighten/darken the screen based on liveness feedback
                    val requiredAction = face.actionRequiredFromApplicationTask?.action
                    when (requiredAction) {
                        ActionRequiredFromApplication.BRIGHTEN_DISPLAY -> {
                            mBackgroundLayout.setBackgroundColor(Color.WHITE)
                            face.actionRequiredFromApplicationTask.setAsCompleted()
                        }
                        ActionRequiredFromApplication.DARKEN_DISPLAY -> {
                            mBackgroundLayout.setBackgroundColor(Color.BLACK)
                            face.actionRequiredFromApplicationTask.setAsCompleted()
                        }
                        ActionRequiredFromApplication.STOP_CAMERA -> {
                            mCameraPreviewLayout.visibility = View.INVISIBLE
                            face.actionRequiredFromApplicationTask.setAsCompleted()
                        }
                        else -> {}
                    }

                    // Display user feedback and warnings on UI
                    if (!mDoneAnalyzing) {
                        var feedbackMessage = mapFeedbackToMessage(FeedbackForFace.NONE)
                        if (face.feedbackForFace != null) {
                            feedbackMessage = mapFeedbackToMessage(face.feedbackForFace)
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
    @Suppress("MemberVisibilityCanBePrivate")
    protected var analyzedListener =
        EventListener<FaceAnalyzedEventArgs> { _, e ->
            val bd = Bundle()
            e.result.use { result ->
                if (result.faces.isNotEmpty()) {
                    // Get the first face in result
                    val face = result.faces.iterator().next()
                    val livenessStatus: LivenessStatus = face.livenessResult?.livenessStatus?: LivenessStatus.FAILED
                    val livenessFailureReason = face.livenessResult?.livenessFailureReason?: LivenessFailureReason.NONE
                    val verifyStatus = face.recognitionResult?.recognitionStatus?:RecognitionStatus.NOT_COMPUTED
                    val verifyConfidence = face.recognitionResult?.confidence?:Float.NaN
                    val resultIdsList: ArrayList<String> = ArrayList()
                    if (face.livenessResult.resultId != null) {
                        resultIdsList.add(face.livenessResult.resultId.toString())
                    }
                    val digest = result.details?.digest?:""
                    val resultIds = resultIdsList.joinToString(",")
                    val analyzedResult = AnalyzedResult(livenessStatus, livenessFailureReason, verifyStatus, verifyConfidence, resultIds, digest)
                    bd.putParcelable("result", analyzedResult)
                } else {
                    val analyzedResult = AnalyzedResult(LivenessStatus.NOT_COMPUTED, LivenessFailureReason.NONE, RecognitionStatus.NOT_COMPUTED, Float.NaN, "", "")
                    bd.putParcelable("result", analyzedResult)
                }
            }

            synchronized(this) {
                if (!mBackPressed) {
                    mResultReceiver?.send(AnalyzedResultType.RESULT, bd)
                }
            }
        }

    @Suppress("MemberVisibilityCanBePrivate")
    protected var stoppedListener =
        EventListener<FaceAnalysisStoppedEventArgs> { _, e ->
            if (e.reason == FaceAnalysisStoppedReason.ERROR) {
                mResultReceiver?.send(AnalyzedResultType.ERROR, null)
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
            mResultReceiver?.send(AnalyzedResultType.ERROR, null)
            return
        }

        mFaceAnalysisOptions = FaceAnalysisOptions()

        mFaceAnalysisOptions?.setFaceSelectionMode(FaceSelectionMode.LARGEST)

        try {
            mFaceAnalyzer?.analyzeOnceAsync(mFaceAnalysisOptions)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
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
        val previewSize = mVisionSource?.cameraPreviewFormat
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
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        synchronized(this) {
            mBackPressed = true
        }
        @Suppress("DEPRECATION") super.onBackPressed()
        mFaceAnalyzer?.stopAnalyzeOnce()
        val bd = Bundle()
        mResultReceiver?.send(AnalyzedResultType.BACKPRESSED, bd)
    }

    private fun mapFeedbackToMessage(feedback : FeedbackForFace): String {
        when(feedback) {
            FeedbackForFace.NONE -> return getString(R.string.feedback_none)
            FeedbackForFace.LOOK_AT_CAMERA -> return getString(R.string.feedback_look_at_camera)
            FeedbackForFace.FACE_NOT_CENTERED -> return getString(R.string.feedback_face_not_centered)
            FeedbackForFace.MOVE_CLOSER -> return getString(R.string.feedback_move_closer)
            FeedbackForFace.CONTINUE_TO_MOVE_CLOSER -> return getString(R.string.feedback_continue_to_move_closer)
            FeedbackForFace.MOVE_BACK -> return getString(R.string.feedback_move_back)
            FeedbackForFace.REDUCE_MOVEMENT -> return getString(R.string.feedback_reduce_movement)
            FeedbackForFace.SMILE -> return getString(R.string.feedback_smile)
            FeedbackForFace.ATTENTION_NOT_NEEDED -> {
                mDoneAnalyzing = true
                return getString(R.string.feedback_attention_not_needed)
            }
        }
    }
}
