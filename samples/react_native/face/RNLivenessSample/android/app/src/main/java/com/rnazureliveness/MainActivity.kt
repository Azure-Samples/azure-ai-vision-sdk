package com.rnazureliveness
import android.content.Intent
import android.os.Bundle;
import android.util.Log
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.facebook.react.modules.core.RCTNativeAppEventEmitter

class MainActivity : ReactActivity() {

  var tag = "fatTag" + MainActivity::class.java.simpleName

  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(null)
  }

  override fun getMainComponentName(): String = "RNAzureLiveness"

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == 1110 && data != null) {
      val status = data.getStringExtra("status")
      if (status == "success") {
        val resultMap = hashMapOf<String, Any?>(
          "status" to "success",
          "resultId" to data.getStringExtra("resultId"),
          "digest" to data.getStringExtra("digest"),
          "data" to data.getStringExtra("data")
        )
        val writableMap = convertToWritableMap(resultMap)
        sendEvent("getLivenessResultAndroid", writableMap)
      } else if (status == "error") {
        val errorMap = hashMapOf<String, Any?>(
          "status" to "error",
          "livenessError" to data.getStringExtra("livenessError"),
          "recognitionError" to data.getStringExtra("recognitionError"),
          "data" to data.getStringExtra("data")
        )
        val writableMap = convertToWritableMap(errorMap)
        sendEvent("getLivenessResultAndroid", writableMap)
      } else {
        val errorMap = hashMapOf<String, Any?>(
          "status" to "error",
          "otherError" to "Something went wrong! Try again later!",
        )
        val writableMap = convertToWritableMap(errorMap)
        sendEvent("getLivenessResultAndroid", writableMap)
      }
    }
  }

  /** Send data from android to React Native */
  private fun sendEvent(eventName: String, params: WritableMap) {
    val reactContext = reactNativeHost.reactInstanceManager.currentReactContext
    reactContext?.getJSModule(RCTNativeAppEventEmitter::class.java)
      ?.emit(eventName, params)
      ?: Log.i(tag, "sendEvent: react context is null")
  }

  private fun convertToWritableMap(map: Map<String, Any?>): WritableMap {
    val writableMap = Arguments.createMap()

    for ((key, value) in map) {
      when (value) {
        is String -> writableMap.putString(key, value)
        is Int -> writableMap.putInt(key, value)
        is Double -> writableMap.putDouble(key, value)
        is Boolean -> writableMap.putBoolean(key, value)
        is WritableMap -> writableMap.putMap(key, value)
        null -> writableMap.putNull(key)
        else -> {
          writableMap.putString(key, value.toString()) // fallback
        }
      }
    }

    return writableMap
  }


  /**
   * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
   * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
   */
  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
}
