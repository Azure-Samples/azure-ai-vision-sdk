/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/// https://github.com/android/camera-samples/blob/master/CameraUtils/lib/src/main/java/com/example/android/camera/utils/AutoFitSurfaceView.kt
package com.example.FaceAnalyzerSample

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.min
import kotlin.math.roundToInt


/**
 * A [SurfaceView] that can be adjusted to a specified aspect ratio and
 * performs center-crop transformation of input frames.
 */
class AutoFitSurfaceView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle) {

    private var aspectRatio = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        if (aspectRatio == 0f) {
            setMeasuredDimension(width, height)
        } else {

            // Performs center-crop transformation of the camera frames
            val newWidth: Int
            val newHeight: Int
            val actualRatio = 1f
            if (width < height * actualRatio) {
                newHeight = height
                newWidth = (height * actualRatio).roundToInt()
            } else {
                newWidth = width
                newHeight = (width / actualRatio).roundToInt()
            }
            setMeasuredDimension(newWidth, newHeight)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            super.dispatchDraw(canvas)
        } else {
            canvas.apply {
                val save = save()
                clipPath(path)
                super@AutoFitSurfaceView.dispatchDraw(canvas)
                restoreToCount(save)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val halfWidth = w / 2f
        val halfHeight = h / 2f
        path.reset()
        path.addCircle(halfWidth, halfHeight, min(halfWidth, halfHeight), Path.Direction.CW)
        path.close()
    }

    companion object {
        private val TAG = AutoFitSurfaceView::class.java.simpleName
    }

    private val path: Path = Path()

    init {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View?, outline: Outline?) {
                    if (view != null && outline != null) {
                        val diameter = min(view.measuredHeight, view.measuredWidth)
                        val rect = Rect((view.measuredWidth - diameter) / 2, (view.measuredHeight - diameter) / 2, (view.measuredWidth + diameter) / 2, (view.measuredHeight + diameter) / 2)
                        outline.setRoundRect(rect, diameter / 2.0f)
                    }
                }
            }
            clipToOutline = true
        }
    }
}