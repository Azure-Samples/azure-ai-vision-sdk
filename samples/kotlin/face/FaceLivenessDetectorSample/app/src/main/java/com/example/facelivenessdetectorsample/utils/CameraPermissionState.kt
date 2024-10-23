package com.example.facelivenessdetectorsample.utils

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun getCameraPermissionState(): PermissionState {
    return when (LocalInspectionMode.current) {
        true -> rememberPreviewPermissionState(granted = false)
        false -> rememberPermissionState(Manifest.permission.CAMERA)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
private class PreviewPermissionState(
    override val status: PermissionStatus
) : PermissionState {

    override val permission: String
        get() = ""

    override fun launchPermissionRequest() {
        // No-op by design.
    }
}

@Composable
@OptIn(ExperimentalPermissionsApi::class)
private fun rememberPreviewPermissionState(granted: Boolean): PermissionState {
    val status = when (granted) {
        true -> PermissionStatus.Granted
        false -> PermissionStatus.Denied(shouldShowRationale = false)
    }

    return remember(granted) { PreviewPermissionState(status = status) }
}
