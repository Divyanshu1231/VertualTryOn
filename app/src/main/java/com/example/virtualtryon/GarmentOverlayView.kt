package com.example.virtualtryon

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class GarmentOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Landmark indices (MediaPipe Pose)
    companion object {
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        const val NOSE = 0
        const val LEFT_ANKLE = 27
        const val RIGHT_ANKLE = 28
    }

    private var results: PoseLandmarkerResult? = null
    private var garmentBitmap: Bitmap? = null
    private var imageWidth = 1
    private var imageHeight = 1
    private var isFrontCamera = true
    private var avatarTone = 0
    private var userHeightCm = 170
    private var garmentFitScale = 0.9f
    private var backgroundRemovalEnabled = false

    private val skeletonPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 4f
        style = Paint.Style.STROKE
        alpha = 180
    }

    private val dotPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(65, 226, 171, 132)
        style = Paint.Style.FILL
    }

    private val garmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 210
        isFilterBitmap = true
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(115, 8, 12, 20)
        style = Paint.Style.FILL
    }

    // Garment drawable assets.
    private val garmentResources = listOf(
        R.drawable.garment_tshirt,
        R.drawable.garment_hoodie,
        R.drawable.garment_jacket,
        R.drawable.garment_kurta,
        R.drawable.garment_shirt
    )

    private var currentGarmentIndex = 0

    fun setGarment(index: Int) {
        currentGarmentIndex = index.coerceIn(0, garmentResources.size - 1)
        garmentBitmap = BitmapFactory.decodeResource(resources,
            garmentResources[currentGarmentIndex])
        invalidate()
    }

    fun setAvatarOptions(tone: Int, heightCm: Int) {
        avatarTone = tone.coerceIn(0, 3)
        userHeightCm = heightCm.coerceIn(120, 220)
        avatarPaint.color = when (avatarTone) {
            1 -> Color.argb(65, 245, 205, 170)
            2 -> Color.argb(65, 196, 137, 92)
            3 -> Color.argb(65, 116, 74, 52)
            else -> Color.argb(65, 226, 171, 132)
        }
        invalidate()
    }

    fun adjustGarmentFit(delta: Float) {
        garmentFitScale = (garmentFitScale + delta).coerceIn(0.75f, 1.35f)
        invalidate()
    }

    fun toggleBackgroundRemoval(): Boolean {
        backgroundRemovalEnabled = !backgroundRemovalEnabled
        invalidate()
        return backgroundRemovalEnabled
    }

    fun setResults(
        poseLandmarkerResult: PoseLandmarkerResult,
        imgWidth: Int,
        imgHeight: Int,
        frontCamera: Boolean
    ) {
        results = poseLandmarkerResult
        imageWidth = imgWidth
        imageHeight = imgHeight
        isFrontCamera = frontCamera
        invalidate()
    }

    fun getMeasurements(): String {
        val landmarks = results?.landmarks()?.firstOrNull() ?: return "No body detected"
        if (landmarks.size < 25) return "Detecting..."

        val leftShoulder = landmarks[LEFT_SHOULDER]
        val rightShoulder = landmarks[RIGHT_SHOULDER]
        val leftHip = landmarks[LEFT_HIP]
        val rightHip = landmarks[RIGHT_HIP]

        val ankleY = max(landmarks[LEFT_ANKLE].y(), landmarks[RIGHT_ANKLE].y())
        val bodyHeightNorm = abs(ankleY - landmarks[NOSE].y()).coerceAtLeast(0.35f)
        val cmPerNorm = userHeightCm / bodyHeightNorm

        val shoulderCm = (abs(leftShoulder.x() - rightShoulder.x()) * cmPerNorm)
            .toInt()
            .coerceIn(34, 58)
        val chestCm = (shoulderCm * 2.05f).toInt().coerceIn(76, 118)
        val waistCm = (abs(leftHip.x() - rightHip.x()) * cmPerNorm * 1.75f)
            .toInt()
            .coerceIn(66, 116)

        return "Shoulder ${shoulderCm}cm | Chest ${chestCm}cm | Waist ${waistCm}cm | ${estimateSize(chestCm)}"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val landmarks = results?.landmarks()?.firstOrNull() ?: return
        if (landmarks.size < 25) return

        fun getX(index: Int): Float {
            val normalized = if (isFrontCamera) 1f - landmarks[index].x() else landmarks[index].x()
            return mapImageX(normalized * imageWidth)
        }
        fun getY(index: Int) = mapImageY(landmarks[index].y() * imageHeight)

        if (backgroundRemovalEnabled) {
            drawBackgroundRemovalMask(canvas, ::getX, ::getY)
        }

        drawAvatarBody(canvas, ::getX, ::getY)

        // Draw skeleton connections
        val connections = listOf(
            11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16,
            11 to 23, 12 to 24, 23 to 24, 23 to 25, 24 to 26,
            25 to 27, 26 to 28
        )
        connections.forEach { (a, b) ->
            canvas.drawLine(getX(a), getY(a), getX(b), getY(b), skeletonPaint)
        }

        // Draw joint dots
        listOf(11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28).forEach { i ->
            canvas.drawCircle(getX(i), getY(i), 8f, dotPaint)
        }

        // Draw garment overlay
        garmentBitmap?.let { bmp ->
            val lsx = getX(LEFT_SHOULDER)
            val rsx = getX(RIGHT_SHOULDER)
            val shoulderCenterX = (lsx + rsx) / 2f
            val hipCenterX = (getX(LEFT_HIP) + getX(RIGHT_HIP)) / 2f
            val centerX = (shoulderCenterX * 0.65f) + (hipCenterX * 0.35f)

            val shoulderWidth = abs(lsx - rsx).coerceAtLeast(width * 0.12f)
            val garmentWidth = shoulderWidth * 2.15f * garmentFitScale
            val left = centerX - garmentWidth / 2f
            val right = centerX + garmentWidth / 2f
            val top = minOf(getY(LEFT_SHOULDER), getY(RIGHT_SHOULDER)) - 20
            val bottom = maxOf(getY(LEFT_HIP), getY(RIGHT_HIP)) + shoulderWidth * 0.45f

            val destRect = RectF(left, top, right, bottom)
            canvas.drawBitmap(bmp, null, destRect, garmentPaint)
        }
    }

    private fun drawBackgroundRemovalMask(
        canvas: Canvas,
        getX: (Int) -> Float,
        getY: (Int) -> Float
    ) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        val bodyCenterX = (getX(LEFT_SHOULDER) + getX(RIGHT_SHOULDER) + getX(LEFT_HIP) + getX(RIGHT_HIP)) / 4f
        val shoulderY = min(getY(LEFT_SHOULDER), getY(RIGHT_SHOULDER))
        val hipY = max(getY(LEFT_HIP), getY(RIGHT_HIP))
        val bodyWidth = abs(getX(LEFT_SHOULDER) - getX(RIGHT_SHOULDER)).coerceAtLeast(width * 0.18f) * 2.2f
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, 255, 255, 255)
            style = Paint.Style.FILL
        }.also { highlight ->
            canvas.drawRoundRect(
                bodyCenterX - bodyWidth / 2f,
                shoulderY - bodyWidth * 0.45f,
                bodyCenterX + bodyWidth / 2f,
                hipY + bodyWidth * 0.75f,
                bodyWidth * 0.35f,
                bodyWidth * 0.35f,
                highlight
            )
        }
    }

    private fun drawAvatarBody(
        canvas: Canvas,
        getX: (Int) -> Float,
        getY: (Int) -> Float
    ) {
        val shoulderLeft = min(getX(LEFT_SHOULDER), getX(RIGHT_SHOULDER))
        val shoulderRight = max(getX(LEFT_SHOULDER), getX(RIGHT_SHOULDER))
        val hipLeft = min(getX(LEFT_HIP), getX(RIGHT_HIP))
        val hipRight = max(getX(LEFT_HIP), getX(RIGHT_HIP))
        val shoulderY = min(getY(LEFT_SHOULDER), getY(RIGHT_SHOULDER))
        val hipY = max(getY(LEFT_HIP), getY(RIGHT_HIP))
        val shoulderWidth = (shoulderRight - shoulderLeft).coerceAtLeast(width * 0.12f)

        val path = Path().apply {
            moveTo(shoulderLeft - shoulderWidth * 0.25f, shoulderY)
            lineTo(shoulderRight + shoulderWidth * 0.25f, shoulderY)
            lineTo(hipRight + shoulderWidth * 0.18f, hipY)
            lineTo(hipLeft - shoulderWidth * 0.18f, hipY)
            close()
        }
        canvas.drawPath(path, avatarPaint)
    }

    private fun estimateSize(chestCm: Int): String {
        return when {
            chestCm < 88 -> "S"
            chestCm < 98 -> "M"
            chestCm < 108 -> "L"
            chestCm < 118 -> "XL"
            else -> "XXL"
        }
    }

    private fun imageScale(): Float {
        if (imageWidth <= 0 || imageHeight <= 0 || width <= 0 || height <= 0) return 1f
        return max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
    }

    private fun mapImageX(x: Float): Float {
        val scale = imageScale()
        val drawnWidth = imageWidth * scale
        val offsetX = (width - drawnWidth) / 2f
        return offsetX + x * scale
    }

    private fun mapImageY(y: Float): Float {
        val scale = imageScale()
        val drawnHeight = imageHeight * scale
        val offsetY = (height - drawnHeight) / 2f
        return offsetY + y * scale
    }
}
