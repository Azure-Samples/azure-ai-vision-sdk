//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

package com.example.FaceAnalyzerSample

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity


/***
 * Settings page for service configuration and app options
 */
class SettingsActivity : AppCompatActivity() {
    var endpointView: TextView? = null
    var keyView: TextView? = null
    var resultIdView: TextView? = null
    var sendResultsToClientCheckboxView: CheckBox? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        endpointView = findViewById(R.id.endpointText)
        keyView = findViewById(R.id.keyText)
        resultIdView = findViewById(R.id.resultIdtextView)
        sendResultsToClientCheckboxView = findViewById(R.id.sendResultsToClient)
        val sharedPref = this.getSharedPreferences("SettingValues", Context.MODE_PRIVATE)
        endpointView!!.text = sharedPref.getString("endpoint", "")
        keyView!!.text = sharedPref.getString("key", "")
        sendResultsToClientCheckboxView!!.isChecked = sharedPref.getBoolean("sendResultsToClient", false)

        resultIdView!!.text = sharedPref.getString("resultIds", "")
        val ctx = this
        resultIdView!!.setOnClickListener({
            if(resultIdView!!.text != "")
            {
                val clipboardManager: ClipboardManager = ctx.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                    @Suppress("DEPRECATION") clipboardManager.setText(resultIdView!!.text)
                } else {
                    val clip = ClipData.newPlainText("Copied Text", resultIdView!!.text)
                    clipboardManager.setPrimaryClip(clip)
                }
                Toast.makeText(ctx, "resultId copied", Toast.LENGTH_SHORT)
            }

        })
    }

    fun onButtonClick(@Suppress("UNUSED_PARAMETER") view: View?) {
        val sharedPref = this.getSharedPreferences("SettingValues", Context.MODE_PRIVATE)
        sharedPref.edit().putString("endpoint", endpointView!!.text.trimEnd().toString()).apply()
        sharedPref.edit().putString("key", keyView!!.text.trimEnd().toString()).apply()
        sharedPref.edit().putBoolean("sendResultsToClient", sendResultsToClientCheckboxView!!.isChecked)
            .apply()
        finish()
    }
}