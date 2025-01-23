package com.microsoft.azure.ai.vision.facelivenessdetectorsample.screens

import android.preference.PreferenceManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign.Companion.Center
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.azure.android.ai.vision.face.ui.FaceLivenessDetector
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.utils.getCameraPermissionState
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.token.FaceSessionToken
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.viewmodel.LivenessScreenViewModel
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.viewmodel.LivenessScreenViewModelFactory
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LivenessScreen(
    navController: NavController,
    viewModel: LivenessScreenViewModel = viewModel(
        factory = LivenessScreenViewModelFactory(
            PreferenceManager.getDefaultSharedPreferences(
                LocalContext.current
            )
        )
    ),
    cameraPermissionState: PermissionState = getCameraPermissionState()
) {

    val analyzedResultResponse by viewModel.analyzedResultResponse.collectAsState()
    val resultData by viewModel.resultData.collectAsState()
    val shouldShowLivenessAnalyze by viewModel.shouldShowLivenessAnalyze.collectAsState()
    val initialPermissionState by viewModel.initialPermissionState.collectAsState()

    // Boolean state variable to track if shouldShowRationale changed from true to false
    var shouldShowRationaleBecameFalseFromTrue by remember { mutableStateOf(false) }

    // Remember the previous value of shouldShowRationale
    var prevShouldShowRationale by remember { mutableStateOf(cameraPermissionState.status.shouldShowRationale) }

    LaunchedEffect(Unit) {
        viewModel.onInitialStateFetch(cameraPermissionState.status.isGranted)
    }

    LaunchedEffect(analyzedResultResponse) {
        if(analyzedResultResponse.livenessDetectionSuccess != null ||
            analyzedResultResponse.livenessDetectionError != null) {
            navController.navigate(resultData)
        }
    }

    // Track changes in shouldShowRationale
    LaunchedEffect(cameraPermissionState.status.shouldShowRationale) {
        if (prevShouldShowRationale && !cameraPermissionState.status.shouldShowRationale) {
            shouldShowRationaleBecameFalseFromTrue = true
        }
        prevShouldShowRationale = cameraPermissionState.status.shouldShowRationale
    }

    // if shouldShowRationale changed from true to false and the permission is not granted,
    // then the user denied the permission and checked the "Never ask again" checkbox
    val userDeniedPermission =
        shouldShowRationaleBecameFalseFromTrue && !cameraPermissionState.status.isGranted

    // If the initial permission is granted, then we directly show the analyze screen, as we don't need permission.
    // If we need permission and have acquired it, then we wait for the user to click the button and verify if the button was clicked by checking viewModel.shouldShowLivenessAnalyze
    if (!(!initialPermissionState && cameraPermissionState.status.isGranted) || shouldShowLivenessAnalyze) {
        FaceLivenessDetector(
            sessionAuthorizationToken = FaceSessionToken.sessionToken,
            verifyImageFileContent = FaceSessionToken.sessionSetInClientVerifyImage,
            deviceCorrelationId = null,
            onSuccess = viewModel::onSuccess,
            onError = viewModel::onError
        )
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val textToShow = if (cameraPermissionState.status.shouldShowRationale) {
                "The camera is required for this app. Please grant the permission."
            } else {
                "Camera permission required for this feature to be available. " + "Please grant the permission."
            }
            Text(text = textToShow, Modifier.padding(16.dp), textAlign = Center)
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text("Request permission")
            }

            Button(
                onClick = { viewModel.showLivenessAnalyze() },
                enabled = cameraPermissionState.status.isGranted
            ) {
                Text(text = "Start liveness")
            }
        }
    }
}
