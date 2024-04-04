package com.example.FaceAnalyzerSample
import android.os.Parcelable
import android.os.ResultReceiver
import kotlinx.parcelize.Parcelize

//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

/***
 * Model class
 */
@Parcelize
data class AnalyzeModel(val endpoint: String, val token: String, val resultReceiver: ResultReceiver?) :
    Parcelable