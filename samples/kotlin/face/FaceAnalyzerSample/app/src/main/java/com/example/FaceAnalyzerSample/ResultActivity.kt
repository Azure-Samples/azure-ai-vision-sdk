//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

package com.example.FaceAnalyzerSample

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/***
 * Displays the FaceAnalyzedResult on the UI.
 */
class ResultActivity : AppCompatActivity() {

    private val viewMap: LinkedHashMap<TextView, TextView> = LinkedHashMap()
    private lateinit var mRetryButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_activity_result)

        viewMap[findViewById(R.id.resultLabel1)] = findViewById(R.id.resultValue1)
        viewMap[findViewById(R.id.resultLabel2)] = findViewById(R.id.resultValue2)
        viewMap[findViewById(R.id.resultLabel3)] = findViewById(R.id.resultValue3)
        viewMap[findViewById(R.id.resultLabel4)] = findViewById(R.id.resultValue4)

        val livenessStatus = intent.getStringExtra("livenessStatus")
        val livenessFailureReason = intent.getStringExtra("livenessFailureReason")
        val verificationStatus = intent.getStringExtra("verificationStatus")
        val verificationConfidence = intent.getStringExtra("verificationConfidence")
        val errorMessage = intent.getStringExtra("error")

        val itr = viewMap.entries.iterator()
        var mapEntry = itr.next()

        if(!errorMessage.isNullOrBlank()){
            mapEntry.key.text = "Error:"
            mapEntry.value.text = errorMessage

        } else {
            // Display liveness results
            if (!livenessStatus.isNullOrBlank()) {
                mapEntry.key.text = "Liveness status:"
                mapEntry.value.text = livenessStatus
                mapEntry = itr.next()
                mapEntry.key.text = "Liveness Failure Reason:"
                mapEntry.value.text = livenessFailureReason
                mapEntry = itr.next()
            }

            if (!verificationStatus.isNullOrBlank()) {
                mapEntry.key.text = "Verification status:"
                mapEntry.value.text = verificationStatus
                mapEntry = itr.next()
                mapEntry.key.text = "Verification confidence:"
                mapEntry.value.text = verificationConfidence
            }
        }

        mRetryButton = findViewById(R.id.retryButton)
        mRetryButton.setOnClickListener { @Suppress("DEPRECATION") super.onBackPressed() }
    }

    /**
     * Overrides back button to always return to main activity
     */
    @Deprecated("Deprecated in Java")
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
