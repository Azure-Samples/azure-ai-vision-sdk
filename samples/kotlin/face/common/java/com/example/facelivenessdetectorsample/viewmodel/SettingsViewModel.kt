package com.microsoft.azure.ai.vision.facelivenessdetectorsample.viewmodel

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.microsoft.azure.ai.vision.facelivenessdetectorsample.utils.SharedPrefKeys
import kotlinx.coroutines.launch

class SettingsViewModel(private val sharedPreferences: SharedPreferences) : ViewModel() {

    var faceApiEndpoint by mutableStateOf(
        sharedPreferences.getString(
            SharedPrefKeys.FACE_API_ENDPOINT, ""
        ) ?: ""
    )
        private set

    var faceApiKey by mutableStateOf(
        sharedPreferences.getString(SharedPrefKeys.FACE_API_KEY, "") ?: ""
    )
        private set

    var lastSessionApimReqId by mutableStateOf(
        sharedPreferences.getString(
            SharedPrefKeys.LAST_SESSION_APIM_REQ_ID, ""
        ) ?: ""
    )
        private set

    var setImageInClient by mutableStateOf(
        sharedPreferences.getBoolean(
            SharedPrefKeys.SET_IMAGE_IN_CLIENT, false
        )
    )
        private set
    var passiveActive by mutableStateOf(
        sharedPreferences.getBoolean(
            SharedPrefKeys.PASSIVE_ACTIVE, false
        )
    )
        private set

    fun updateFaceApiEndpoint(value: String) {
        faceApiEndpoint = value
        sharedPreferences.edit().putString(SharedPrefKeys.FACE_API_ENDPOINT, value).apply()
    }

    fun updateFaceApiKey(value: String) {
        faceApiKey = value
        sharedPreferences.edit().putString(SharedPrefKeys.FACE_API_KEY, value).apply()
    }


    fun updatesetImageInClient(value: Boolean) {
        setImageInClient = value
        sharedPreferences.edit().putBoolean(SharedPrefKeys.SET_IMAGE_IN_CLIENT, value).apply()
    }

    fun updatesetPassiveActive(value: Boolean) {
        passiveActive = value
        sharedPreferences.edit().putBoolean(SharedPrefKeys.PASSIVE_ACTIVE, value).apply()
    }

    init {
        viewModelScope.launch {
            lastSessionApimReqId =
                sharedPreferences.getString(SharedPrefKeys.LAST_SESSION_APIM_REQ_ID, "") ?: ""

        }
    }
}
