package com.example.virtualtryon

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseDetectorHelper(
    private val context: Context,
    private val onResult: (PoseLandmarkerResult, Int, Int) -> Unit
) {
    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    private fun setupPoseLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("models/pose_landmarker_lite.task")
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, image ->
                onResult(result, image.width, image.height)
            }
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(context, options)
    }

    fun detectAsync(bitmap: Bitmap, frameTime: Long) {
        val mpImage = BitmapImageBuilder(bitmap).build()
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    fun close() {
        poseLandmarker?.close()
    }
}
