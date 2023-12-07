//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

package com.example.FaceAnalyzerSample

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/***
 * Displays the FaceAnalyzedResult on the UI.
 */
class ResultActivity : AppCompatActivity() {

    private val viewMap: LinkedHashMap<TextView, TextView> = LinkedHashMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_activity_result)

        viewMap[findViewById(R.id.resultLabel1)] = findViewById(R.id.resultValue1)
        viewMap[findViewById(R.id.resultLabel2)] = findViewById(R.id.resultValue2)
        viewMap[findViewById(R.id.resultLabel3)] = findViewById(R.id.resultValue3)
        viewMap[findViewById(R.id.resultLabel4)] = findViewById(R.id.resultValue4)

        var livenessStatus = intent.getStringExtra("livenessStatus")
        var livenessFailureReason = intent.getStringExtra("livenessFailureReason")
        var verificationStatus = intent.getStringExtra("verificationStatus")
        var verificationConfidence = intent.getStringExtra("verificationConfidence")
        var errorMessage = intent.getStringExtra("error")

        var itr = viewMap.entries.iterator()
        var mapEntry = itr.next()

        if(errorMessage.isNullOrBlank() == false){
            mapEntry.key.text = "Error:"
            mapEntry.value.text = errorMessage

        } else {
            // Display liveness results
            if (livenessStatus.isNullOrBlank() == false) {
                mapEntry.key.text = "Liveness status:"
                mapEntry.value.text = livenessStatus
                mapEntry = itr.next()
                mapEntry.key.text = "Liveness Failure Reason:"
                mapEntry.value.text = livenessFailureReason
                mapEntry = itr.next()
            }

            if (verificationStatus.isNullOrBlank() == false) {
                mapEntry.key.text = "Verification status:"
                mapEntry.value.text = verificationStatus
                mapEntry = itr.next()
                mapEntry.key.text = "Verification confidence:"
                mapEntry.value.text = verificationConfidence
            }
        }
    }

    /**
     * Overrides back button to always return to main activity
     */
    override fun onBackPressed() {
        @Suppress("DEPRECATION") super.onBackPressed()
        for (entry in viewMap.entries) {
            entry.key.text = ""
            entry.value.text = ""
        }
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }
}
