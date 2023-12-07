package com.example.FaceAnalyzerSample

import androidx.annotation.IntDef

class AnalyzedResultType {
    companion object {

        @IntDef(RESULT, BACKPRESSED, ERROR)
        @Retention(AnnotationRetention.SOURCE)
        annotation class AnalyzeResultType
        const val RESULT = 0
        const val BACKPRESSED = 1
        const val ERROR = 2
    }
}
