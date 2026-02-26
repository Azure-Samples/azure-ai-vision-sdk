package com.microsoft.azure.ai.vision.facelivenessdetectorsample.navigation

import ResultScreen
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.models.ResultData
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.screens.LivenessScreen
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.screens.MainScreen
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.screens.SettingsScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi

object Routes {
    const val Main = "main"
    const val Settings = "settings"
    const val Liveness = "liveness"
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AppNavHost(navController: NavHostController, startDestination: String = Routes.Main) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.Main) {
            MainScreen(navController)
        }
        composable(Routes.Settings) {
            SettingsScreen(navController)
        }
        composable(Routes.Liveness) {
            LivenessScreen(navController)
        }
        composable<ResultData> { backStackEntry ->
            val result: ResultData = backStackEntry.toRoute()
            ResultScreen(navController, result)
        }
    }
}