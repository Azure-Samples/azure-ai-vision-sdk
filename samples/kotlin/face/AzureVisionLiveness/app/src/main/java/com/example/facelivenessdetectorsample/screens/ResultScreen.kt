import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.models.ResultData
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.navigation.Routes
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.token.FaceSessionToken
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.ui.components.MicrosoftBranding
import com.azure.android.ai.vision.face.deviceattestation.AttestationSession
import com.azure.android.ai.vision.face.deviceattestation.DeviceAttestation
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.utils.getDeviceIdExt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ResultScreen(navController: NavController, resultData: ResultData) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        // Post digest if present in resultData and in quicklink mode
        if (FaceSessionToken.quickLink == true) {
            resultData.digest?.let { digest ->
                if (digest.isNotEmpty()) {
                    val sessionId = FaceSessionToken.sessionId
                    val clientId = FaceSessionToken.deviceCorrelationIdInClient

                    if (sessionId.isNotEmpty() && !clientId.isNullOrEmpty()) {
                        val session = DeviceAttestation.currentSession()
                        if (session == null) {
                            println("No active attestation session for sessionId=$sessionId")
                        } else {
                            when (val result = session.submitLivenessDigest(digest)) {
                                is AttestationSession.LivenessDigestResult.Success -> {
                                    println("Digest posted successfully")
                                }
                                is AttestationSession.LivenessDigestResult.Error -> {
                                    println("Failed to post digest: ${result.code} - ${result.message}")
                                }
                                is AttestationSession.LivenessDigestResult.Exception -> {
                                    println("Exception posting digest: ${result.exception.message}")
                                    result.exception.printStackTrace()
                                }
                            }
                        }
                    }
                }
            }
        }

        FaceSessionToken.callbackUrl?.let {
            FaceSessionToken.callbackUrl = null
            // The callbackUrl arrives on the inbound App Link and is therefore
            // attacker-controllable. Only follow it when it points back at THIS
            // session's liveness host over https — the host was resolved from the
            // inbound link and validated against the whitelist at launch, and the
            // backend only ever sets callbackUrl to its own /result page on that
            // host. Anything else is an open redirect and is dropped.
            val uri = it.toUri()
            val host = uri.host
            val sessionHost = FaceSessionToken.livenessHost
            if (uri.scheme.equals("https", ignoreCase = true) &&
                host != null &&
                sessionHost.isNotEmpty() &&
                host.equals(sessionHost, ignoreCase = true)
            ) {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } else {
                println("Ignoring callbackUrl with unexpected host: ${uri.host}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            "Check Complete",
            fontSize = 24.sp,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .semantics { contentDescription = "livenessResults" }
        )
        if(FaceSessionToken.quickLink == false)
        {
            resultData.livenessStatus?.let {
                Text("Liveness status:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    it,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .semantics { contentDescription = "livenessStatus" }
                )
            }
            resultData.livenessFailureReason?.let {
                Text("Liveness failure reason:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    it,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .semantics { contentDescription ="livenessFailureReason" }
                )
            }
            resultData.verificationStatus?.let {
                Text("Verification status:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(it, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
            }
            resultData.verificationConfidence?.let {
                Text("Verification confidence:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(it, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
            }

            Spacer(modifier = Modifier.weight(1f))
            if(resultData.livenessStatus != null ||
                resultData.livenessFailureReason != null ||
                resultData.verificationConfidence != null||
                resultData.verificationStatus != null)
            {
                Button(
                    onClick = {
                        FaceSessionToken.sessionToken = ""
                        FaceSessionToken.callbackUrl = null
                        FaceSessionToken.deviceCorrelationIdInClient = null
                        FaceSessionToken.isVerifyImage = false
                        FaceSessionToken.verificationStatus = null
                        FaceSessionToken.verificationMatchConfidence = null
                        FaceSessionToken.sessionSetInClientVerifyImage = null
                        navController.navigate(Routes.Main) {
                            popUpTo(Routes.Main) {
                                inclusive = true
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .semantics { contentDescription = "mainScreenButton" }
                ) {
                    Text("Go to the main screen")
                }
                Button(
                    onClick = {
                        navController.navigate(Routes.Liveness) {
                            launchSingleTop = true
                            popUpTo(Routes.Main) {
                                inclusive = true
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .semantics { contentDescription = "retryButton" }
                ) {
                    Text("Retry with the same token")
                }
            }
            else{
                FaceSessionToken.sessionToken = ""
            }
        }

        MicrosoftBranding(
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
