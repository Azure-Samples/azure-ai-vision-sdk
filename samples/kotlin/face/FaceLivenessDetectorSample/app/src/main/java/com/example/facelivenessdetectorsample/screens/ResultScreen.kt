import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.models.ResultData
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.navigation.Routes
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.token.FaceSessionToken

@Composable
fun ResultScreen(navController: NavController, resultData: ResultData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            "Results",
            fontSize = 24.sp,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .semantics { contentDescription = "livenessResults" }
        )

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

        Button(
            onClick = {
                FaceSessionToken.sessionToken = ""
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
}
