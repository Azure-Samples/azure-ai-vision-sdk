package com.microsoft.azurevisionliveness

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BlurMaskFilter
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.navigation.AppNavHost
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.navigation.Routes
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.token.FaceSessionToken
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.ui.theme.FaceLivenessDetectorSampleTheme
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlin.math.max

class AppCenterActivity : ComponentActivity() {
    val cAppRequestCode = 1

    private companion object {
        // Persisted flag marking that the one-shot Play install referrer has
        // already been read. Set after the first launch so the referrer is never
        // used again (it would be stale on any subsequent launch).
        const val PREFS_NAME = "app_launch_state"
        const val KEY_REFERRER_CONSUMED = "install_referrer_consumed"

        // Max age of the referrer click before we treat the deep link as stale.
        // The liveness session it points at is short-lived, so only act on a
        // referrer whose click happened within the last 10 minutes.
        const val REFERRER_MAX_AGE_SECONDS = 10L * 60L
    }

    private var authStage by mutableStateOf("Establishing secure session…")
    private var authError by mutableStateOf<String?>(null)
    @Composable
    fun FaceIconScreen() {
        val context = LocalContext.current
        var taps by rememberSaveable { mutableStateOf(0) }
        val unlocked = taps >= 10
        val infiniteTransition = rememberInfiniteTransition()
        // Breathing animation (scale in/out)
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse
            )
        )
        val learnMoreUrl = "https://www.microsoft.com/privacy/data-privacy-notice"

        val density = LocalDensity.current
        val blurDp = 120.dp
        val blurPx = with(density) { blurDp.toPx() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    taps += 1
                    if (taps == 10) {
                        Toast.makeText(context, "Unlocked!", Toast.LENGTH_SHORT).show()
                    }
                }
                .background(Color(0xFF0D47A1)), // deep blue background; change if you like

            contentAlignment = Alignment.Center
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black), // background base
                contentAlignment = Alignment.Center
            ) {
                // Draw the two large blurred gradient circles on a full-screen canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    drawIntoCanvas { canvas ->
                        val w = size.width
                        val h = size.height
                        val maxDim = max(w, h)

                        // Diameter like SwiftUI: max(width, height) * 0.9
                        val diameter = maxDim * 0.9f
                        val radius = diameter / 2f

                        // centers (matching the Swift offsets)
                        val center1 = Offset(w / 2f - w * 0.2f, h / 2f - h * 0.25f)
                        val center2 = Offset(w / 2f + w * 0.25f, h / 2f + h * 0.2f)

                        // --- Circle 1 paint (purple -> blue) ---
                        val paint1 = android.graphics.Paint().apply {
                            isAntiAlias = true
                            // shader: linear gradient across a large rectangle
                            shader = android.graphics.LinearGradient(
                                0f, 0f, maxDim, maxDim,
                                intArrayOf(
                                    Color(0xFF9C27B0).toArgb(), // purple
                                    Color(0xFF2196F3).toArgb()  // blue
                                ),
                                null,
                                android.graphics.Shader.TileMode.CLAMP
                            )
                            // soft blur
                            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                        }

                        // --- Circle 2 paint (cyan -> indigo) ---
                        val paint2 = android.graphics.Paint().apply {
                            isAntiAlias = true
                            shader = android.graphics.LinearGradient(
                                maxDim, 0f, 0f, maxDim,
                                intArrayOf(
                                    Color(0xFF00FFFF).copy(alpha = 0.6f).toArgb(), // cyan-ish
                                    Color(0xFF4B0082).copy(alpha = 0.6f).toArgb()  // indigo
                                ),
                                null,
                                android.graphics.Shader.TileMode.CLAMP
                            )
                            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                        }

                        // draw the blurred circles (they can extend beyond the canvas bounds if larger than screen)
                        canvas.nativeCanvas.drawCircle(center1.x, center1.y, radius, paint1)
                        canvas.nativeCanvas.drawCircle(center2.x, center2.y, radius, paint2)
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Icon
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Face Icon",
                        modifier = Modifier
                            .size(64.dp)
                            .scale(scale)
                            .shadow(12.dp, CircleShape),
                        tint = Color.White.copy(alpha = 0.95f)
                    )

                    // Title
                    Text(
                        text = "We care about your privacy and security",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    // Body copy
                    Text(
                        text = "Microsoft's facial recognition software will check your selfie to generate a liveness score. Your selfie will not be stored after this analysis.",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )

                    // Learn more button
                    Text(
                        text = "Learn more",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(learnMoreUrl))
                            )
                        },
                        textAlign = TextAlign.Center
                    )
                }



                    // Hidden button that appears after 10 taps
                AnimatedVisibility(
                    visible = unlocked,
                    enter = fadeIn(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                ) {
                    val navController = rememberNavController()
                    FaceLivenessDetectorSampleTheme {
                        // A surface container using the 'background' color from the theme
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            AppNavHost(navController, Routes.Main)
                        }
                    }
                }
            }
        }
    }
    fun onCameraSuccess() {
        val action: String? = intent?.action
        val data: Uri? = intent?.data
        if (Intent.ACTION_VIEW == action && data != null) {
            // Normal App Link launch: the app was opened directly by tapping a
            // verified https link, so it already carries the App Link URL. The
            // install referrer is irrelevant on this path and must be ignored.
            // Burn the one-shot flag too: if this App Link open was the very
            // first launch, a later non-App-Link launch would otherwise still
            // pick up the (now stale) referrer from the original install.
            markReferrerConsumed()
            startSessionFromAppLink(data)
        }
        else
        {
            // No App Link URL on this launch. If this is the very first launch
            // after install, the link the user tapped before the app existed was
            // forwarded by Google Play as the install referrer (the backend sets
            // it to the same URL the QR code encodes). Consume it exactly once
            // and treat it as the App Link URL. On any later launch the referrer
            // has already been consumed, so just show the home screen.
            consumeInstallReferrerOnce(
                onAppLink = { referrerUri -> startSessionFromAppLink(referrerUri) },
                onNone = { showHomeScreen() }
            )
        }
    }

    /**
     * Drives the post-launch auth flow from an App Link [data] URI, whether it
     * arrived as a real ACTION_VIEW intent or was recovered from the Play
     * install referrer on first launch. Reads the `s` (session id) and
     * `callbackUrl` query params, then kicks off [runAppLinkAuthFlow]. Falls
     * back to the home screen when the URI carries no session id.
     */
    private fun startSessionFromAppLink(data: Uri) {
        val sParam = data.getQueryParameter("s")
        val cbParam = data.getQueryParameter("callbackUrl")
        // The backend host comes from the inbound App Link URL itself. The OS
        // only delivers links from the app's verified associated domains, but
        // validate it against the BuildConfig.LIVENESS_HOSTS whitelist anyway
        // (defense in depth) before trusting it as the attestation backend. An
        // unrecognized host falls back to the home screen below.
        val linkHost = data.host
        val livenessHost = BuildConfig.LIVENESS_HOSTS.firstOrNull {
            it.equals(linkHost, ignoreCase = true)
        }
        FaceSessionToken.callbackUrl = null
        if (!cbParam.isNullOrEmpty()) {
            val decoded: String? = try {
                URLDecoder.decode(cbParam, StandardCharsets.UTF_8.toString())
            } catch (e: IllegalArgumentException) {
                null
            }
            FaceSessionToken.callbackUrl = decoded
        }
        if (!sParam.isNullOrEmpty() && livenessHost != null) {
            FaceSessionToken.sessionId = sParam
            FaceSessionToken.livenessHost = livenessHost

            // Show the progress overlay immediately so the user has feedback
            // during the 5-10s attestation + token acquisition chain. The
            // chain itself runs in [runAppLinkAuthFlow]; this activity just
            // owns the UI bindings.
            authStage = "Establishing secure session…"
            authError = null
            setContent {
                FaceLivenessDetectorSampleTheme {
                    LinkAuthProgressScreen(
                        stage = authStage,
                        errorMessage = authError,
                        onErrorDismiss = {
                            authError = null
                            finish()
                        }
                    )
                }
            }

            GlobalScope.launch {
                runAppLinkAuthFlow(
                    context = this@AppCenterActivity,
                    sParam = sParam,
                    livenessHost = livenessHost,
                    onStage = { authStage = it },
                    onError = { authError = it },
                    onSuccess = {
                        setContent {
                            val navController = rememberNavController()
                            FaceLivenessDetectorSampleTheme {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    AppNavHost(navController, Routes.Liveness)
                                }
                            }
                        }
                    }
                )
            }
        } else {
            showHomeScreen()
        }
    }

    /**
     * Permanently marks the one-shot Play install referrer as consumed so it is
     * never read again. Called on every first launch outcome — App Link open,
     * referrer recovery, or a missing/stale referrer — so the referrer can only
     * ever influence the single first launch after install.
     */
    private fun markReferrerConsumed() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REFERRER_CONSUMED, true)
            .apply()
    }

    /** Renders the default home/landing UI when there is no session to launch. */
    private fun showHomeScreen() {
        setContent {
            val navController = rememberNavController()
            FaceLivenessDetectorSampleTheme {
                FaceIconScreen()
            }
        }
    }

    /**
     * Reads the Google Play install referrer, but only on the very first launch
     * after installation. The referrer is a one-shot signal that only carries
     * the original deep link when the user reached the app via the Play Store
     * fallback; on every subsequent launch it would be stale, so we gate it on a
     * persisted "already consumed" flag and never read it again afterwards.
     *
     * Even on that first launch the referrer is only used when its click
     * timestamp is within [REFERRER_MAX_AGE_SECONDS] (10 minutes), since the
     * liveness session it points at is short-lived; an older click is dropped.
     *
     * @param onAppLink invoked (on the main thread) with the recovered App Link
     *                  URI when the referrer holds a valid, fresh session URL.
     * @param onNone    invoked (on the main thread) when there is no usable
     *                  referrer — already consumed, missing, stale, or
     *                  unparseable.
     */
    private fun consumeInstallReferrerOnce(
        onAppLink: (Uri) -> Unit,
        onNone: () -> Unit
    ) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REFERRER_CONSUMED, false)) {
            // Not the first launch — the referrer is only meaningful right after
            // install and must never override a normal cold start.
            onNone()
            return
        }

        val referrerClient = InstallReferrerClient.newBuilder(this).build()
        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                var appLink: Uri? = null
                try {
                    if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                        val details = referrerClient.installReferrer
                        // Only honor a fresh referrer: the click must have
                        // happened within the last 10 minutes. A stale referrer
                        // (older install, or the user revisiting much later)
                        // points at an expired liveness session, so ignore it.
                        val clickSeconds = details.referrerClickTimestampSeconds
                        val ageSeconds = (System.currentTimeMillis() / 1000) - clickSeconds
                        if (clickSeconds > 0 && ageSeconds in 0..REFERRER_MAX_AGE_SECONDS) {
                            appLink = parseReferrerAppLink(details.installReferrer)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        referrerClient.endConnection()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                // Mark consumed regardless of the outcome: the referrer is a
                // one-shot install signal, so any later launch must ignore it.
                markReferrerConsumed()
                runOnUiThread {
                    val link = appLink
                    if (link != null) onAppLink(link) else onNone()
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                // Couldn't reach the referrer service; fall back to the home
                // screen without burning the one-shot flag so a later launch can
                // retry while still being the (effectively) first usable launch.
                runOnUiThread { onNone() }
            }
        })
    }

    /**
     * Turns a Play install referrer string into an App Link [Uri], or null when
     * it doesn't carry a session id. The backend sets the referrer to the same
     * URL the QR code encodes (https://host/native?s=…&callbackUrl=…). Most Play
     * versions hand it back already URL-decoded; some return it still
     * percent-encoded as one opaque string, so we decode once and retry when the
     * `s` parameter is missing on the first parse.
     */
    private fun parseReferrerAppLink(referrer: String?): Uri? {
        if (referrer.isNullOrEmpty()) return null
        var uri = runCatching { Uri.parse(referrer) }.getOrNull()
        if (uri?.getQueryParameter("s").isNullOrEmpty() && referrer.contains('%')) {
            val decoded = try {
                URLDecoder.decode(referrer, StandardCharsets.UTF_8.toString())
            } catch (e: IllegalArgumentException) {
                null
            }
            if (decoded != null) {
                uri = runCatching { Uri.parse(decoded) }.getOrNull()
            }
        }
        return if (!uri?.getQueryParameter("s").isNullOrEmpty()) uri else null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()

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
        else{
            runOnUiThread {
                onCameraSuccess()
            }
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
                        runOnUiThread {
                            setContent {
                                val navController = rememberNavController()
                                FaceLivenessDetectorSampleTheme {
                                    Text("Camera permission required")
                                }
                            }

                        }
                    }
                    else {
                        runOnUiThread {
                            onCameraSuccess()
                        }
                    }
                }
            }
        }
    }
}
