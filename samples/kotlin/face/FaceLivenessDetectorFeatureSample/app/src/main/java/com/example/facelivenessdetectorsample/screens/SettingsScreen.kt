package com.microsoft.azure.ai.vision.facelivenessdetectorsample.screens

import com.microsoft.azure.ai.vision.facelivenessdetectorsample.viewmodel.SettingsViewModelFactory
import android.preference.PreferenceManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.navigation.Routes
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.token.FaceSessionToken
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.viewmodel.SettingsViewModel

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    SettingsScreen(rememberNavController())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController, settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(
            // TODO: get specific shared preferences for settings.
            PreferenceManager.getDefaultSharedPreferences(
                LocalContext.current
            )
        )

    )
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(30.dp)
    ) {
        Text(
            text = "Face API endpoint:", fontSize = 16.sp, modifier = Modifier.padding(top = 15.dp)
        )
        TextField(
            value = settingsViewModel.faceApiEndpoint,
            placeholder = { Text("https://") },
            onValueChange = settingsViewModel::updateFaceApiEndpoint,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 15.dp)
        )

        Text(
            text = "Face API key:", fontSize = 16.sp, modifier = Modifier.padding(top = 15.dp)
        )
        TextField(value = settingsViewModel.faceApiKey,
            onValueChange = settingsViewModel::updateFaceApiKey,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            placeholder = { Text("00000000000000000000000000000000") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 15.dp),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
//                        TODO: update icons with eye opened and eye closed icons.
                        imageVector = if (passwordVisible) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            })

        Text(
            text = "Last session apim-request-id",
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 15.dp)
        )
        Text(
            text = settingsViewModel.lastSessionApimReqId,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 15.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 30.dp)
                .semantics(mergeDescendants =  true) { }
        ) {
            Checkbox(
                checked = settingsViewModel.setImageInClient,
                onCheckedChange = settingsViewModel::updatesetImageInClient
            )
            Text(text = "setImageInClient", fontSize = 16.sp, modifier = Modifier.semantics {
                contentDescription = "Set image in client"
            })
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 30.dp)
                .semantics(mergeDescendants =  true) { }
        ) {
            Checkbox(
                checked = settingsViewModel.passiveActive,
                onCheckedChange = settingsViewModel::updatesetPassiveActive
            )
            Text(text = "PassiveActive", fontSize = 16.sp, modifier = Modifier.semantics {
                contentDescription = "Passive active"
            })
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                FaceSessionToken.sessionToken = ""
                navController.navigate(Routes.Main)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 30.dp)
                .align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)) // Change the button color as needed
        ) {
            Text(text = "SAVE", color = Color.White)
        }
    }
}