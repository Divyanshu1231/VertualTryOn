package com.example.virtualtryon

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: GarmentOverlayView
    private lateinit var tvMeasurements: TextView
    private lateinit var tvTrackingStatus: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseDetectorHelper: PoseDetectorHelper
    private lateinit var historyDb: TryOnHistoryDbHelper
    private lateinit var firebaseTracker: FirebaseTracker
    private lateinit var tfLiteBodyHelper: TFLiteBodyHelper

    private var useFrontCamera = true
    private var currentGarmentIndex = 0
    private var avatarTone = 0
    private var heightCm = 170
    private var lastAnalyzedTimeMs = 0L
    private var lastSavedHistoryMs = 0L
    private val totalGarments = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.previewView)
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        overlayView = findViewById(R.id.overlayView)
        tvMeasurements = findViewById(R.id.tvMeasurements)
        tvTrackingStatus = findViewById(R.id.tvTrackingStatus)

        useFrontCamera = intent.getBooleanExtra("use_front_camera", true)
        currentGarmentIndex = intent.getIntExtra("garment_index", 0)
        avatarTone = intent.getIntExtra("avatar_tone", 0)
        heightCm = intent.getIntExtra("height_cm", 170).coerceIn(120, 220)

        cameraExecutor = Executors.newSingleThreadExecutor()
        historyDb = TryOnHistoryDbHelper(this)
        firebaseTracker = FirebaseTracker(this)
        tfLiteBodyHelper = TFLiteBodyHelper(this).also { it.logAvailability() }

        createPoseDetector()

        overlayView.setGarment(currentGarmentIndex)
        overlayView.setAvatarOptions(avatarTone, heightCm)
        startCamera()

        // Button listeners
        findViewById<Button>(R.id.btnSwitchCamera).setOnClickListener {
            useFrontCamera = !useFrontCamera
            lastAnalyzedTimeMs = 0L
            tvTrackingStatus.text = "Tracking: starting"
            poseDetectorHelper.close()
            createPoseDetector()
            startCamera()
        }

        findViewById<Button>(R.id.btnPrevGarment).setOnClickListener {
            currentGarmentIndex = (currentGarmentIndex - 1 + totalGarments) % totalGarments
            overlayView.setGarment(currentGarmentIndex)
        }

        findViewById<Button>(R.id.btnNextGarment).setOnClickListener {
            currentGarmentIndex = (currentGarmentIndex + 1) % totalGarments
            overlayView.setGarment(currentGarmentIndex)
        }

        findViewById<Button>(R.id.btnFitSmaller).setOnClickListener {
            overlayView.adjustGarmentFit(-0.05f)
        }

        findViewById<Button>(R.id.btnFitLarger).setOnClickListener {
            overlayView.adjustGarmentFit(0.05f)
        }

        findViewById<Button>(R.id.btnBackground).setOnClickListener {
            val enabled = overlayView.toggleBackgroundRemoval()
            firebaseTracker.logTryOnEvent("background_removal_toggle", currentGarmentIndex, cameraName())
            Toast.makeText(
                this,
                if (enabled) "Background removal on" else "Background removal off",
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<Button>(R.id.btnShare).setOnClickListener {
            shareTryOnSnapshot()
        }
    }

    private fun startCamera() {
        lastAnalyzedTimeMs = 0L
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        try {
                            val frameTimeMs = SystemClock.uptimeMillis()
                            if (lastAnalyzedTimeMs > 0L && frameTimeMs - lastAnalyzedTimeMs < 90L) {
                                return@setAnalyzer
                            }
                            lastAnalyzedTimeMs = frameTimeMs
                            val bitmap = imageProxy.toBitmap().rotate(imageProxy.imageInfo.rotationDegrees)
                            poseDetectorHelper.detectAsync(bitmap, frameTimeMs)
                        } catch (e: Exception) {
                            Log.e("CameraActivity", "Pose detection failed", e)
                        } finally {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = if (useFrontCamera)
                CameraSelector.DEFAULT_FRONT_CAMERA
            else
                CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (e: Exception) {
                Log.e("CameraActivity", "Camera binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun createPoseDetector() {
        poseDetectorHelper = PoseDetectorHelper(this) { result, w, h ->
            runOnUiThread {
                overlayView.setResults(result, w, h, useFrontCamera)
                tvMeasurements.text = overlayView.getMeasurements()
                tvTrackingStatus.text = if (result.landmarks().firstOrNull().isNullOrEmpty()) {
                    "Tracking: searching"
                } else {
                    saveHistoryIfNeeded(tvMeasurements.text.toString())
                    "Tracking: body locked"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        poseDetectorHelper.close()
        tfLiteBodyHelper.close()
        historyDb.close()
    }

    private fun Bitmap.rotate(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun saveHistoryIfNeeded(measurements: String) {
        val now = System.currentTimeMillis()
        if (now - lastSavedHistoryMs < 4_000L) return
        lastSavedHistoryMs = now
        historyDb.saveTryOn(currentGarmentIndex, cameraName(), measurements)
        firebaseTracker.logTryOnEvent("try_on_locked", currentGarmentIndex, cameraName())
    }

    private fun shareTryOnSnapshot() {
        try {
            val root = window.decorView.rootView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))

            val shareDir = File(cacheDir, "shared").apply { mkdirs() }
            val imageFile = File(shareDir, "virtual_try_on.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                imageFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            firebaseTracker.logTryOnEvent("share_try_on", currentGarmentIndex, cameraName())
            startActivity(Intent.createChooser(shareIntent, "Share try-on"))
        } catch (e: Exception) {
            Log.e("CameraActivity", "Share failed", e)
            Toast.makeText(this, "Unable to share snapshot", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cameraName(): String = if (useFrontCamera) "front" else "back"
}
