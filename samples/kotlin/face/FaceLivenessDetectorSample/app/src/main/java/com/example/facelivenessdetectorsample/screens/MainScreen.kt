package com.example.facelivenessdetectorsample.screens

import android.preference.PreferenceManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.facelivenessdetectorsample.navigation.Routes
import com.example.facelivenessdetectorsample.utils.getDeviceIdExt
import com.example.facelivenessdetectorsample.viewmodel.MainScreenViewModel
import com.example.facelivenessdetectorsample.viewmodel.MainScreenViewModelFactory

@Preview(showBackground = true)
@Composable
fun MainPreview() {
    MainScreen(navController = rememberNavController())
}

// TODO: improve UI telling the user about the current state of token fetching.
@Composable
fun MainScreen(
    navController: NavController, mainScreenViewModel: MainScreenViewModel = viewModel(
        factory = MainScreenViewModelFactory(
            // TODO: get specific shared preferences for settings.
            PreferenceManager.getDefaultSharedPreferences(
                LocalContext.current
            )
        )
    )
) {
    val isTokenReady by mainScreenViewModel.isTokenReady.collectAsState()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.openInputStream(uri)?.readBytes()?.let {
                    mainScreenViewModel.updateVerifyImage(it) {
                        navController.navigate(Routes.Liveness)
                    }
                }
            } ?: run {
                Log.e("MainScreen", "Image selection failed")
            }
        })

    val deviceId = LocalContext.current.getDeviceIdExt()

    LaunchedEffect(true) {
        mainScreenViewModel.deviceId = deviceId
        mainScreenViewModel.fetchFaceAPISessionToken(
            faceApiEndpoint = mainScreenViewModel.faceApiEndpoint,
            faceApiKey = mainScreenViewModel.faceApiKey,
            verifyImageArray = null,
            sendResultsToClient = mainScreenViewModel.sendResultsToClient,
            setImageInClient = mainScreenViewModel.setImageInClient,
            passiveActive = mainScreenViewModel.passiveActive,
            deviceId = deviceId,
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        if (mainScreenViewModel.isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Fetching token, please wait...")
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxHeight()
            ) {

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(30.dp),
                    modifier = Modifier
                        .padding(top = 30.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Button(
                        onClick = { navController.navigate(Routes.Liveness) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        enabled = isTokenReady
                    ) {
                        Text("Liveness")
                    }

                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        enabled = isTokenReady,
                    ) {
                        Text("Liveness with verification")
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(30.dp),
                    modifier = Modifier
                        .padding(bottom = 30.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Button(
                        onClick = { navController.navigate(Routes.Settings) },
                    ) {
                        Text("Settings")
                    }

                    Button(
                        onClick = { /* Handle click */ },
                    ) {
                        Text("Select verification image")
                    }
                }
            }
        }
    }
}