package com.example.virtualtryon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val CAMERA_PERMISSION_CODE = 100
    private var useFrontCamera = true
    private var pendingGarmentIndex = 0
    private lateinit var spinnerGarment: Spinner
    private lateinit var spinnerAvatarTone: Spinner
    private lateinit var etHeightCm: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val garments = listOf("T-Shirt", "Hoodie", "Jacket", "Kurta", "Shirt")
        spinnerGarment = findViewById(R.id.spinnerGarment)
        spinnerGarment.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, garments)

        val tones = listOf("Warm", "Light", "Medium", "Deep")
        spinnerAvatarTone = findViewById(R.id.spinnerAvatarTone)
        spinnerAvatarTone.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, tones)

        etHeightCm = findViewById(R.id.etHeightCm)

        val rgCamera = findViewById<RadioGroup>(R.id.rgCamera)
        rgCamera.setOnCheckedChangeListener { _, checkedId ->
            useFrontCamera = checkedId == R.id.rbFront
        }

        findViewById<Button>(R.id.btnStartCamera).setOnClickListener {
            pendingGarmentIndex = spinnerGarment.selectedItemPosition
            if (hasCameraPermission()) {
                openCamera(pendingGarmentIndex)
            } else {
                requestCameraPermission()
            }
        }
    }

    private fun openCamera(garmentIndex: Int) {
        val heightCm = etHeightCm.text.toString().toIntOrNull()?.coerceIn(120, 220) ?: 170
        val intent = Intent(this, CameraActivity::class.java).apply {
            putExtra("garment_index", garmentIndex)
            putExtra("use_front_camera", useFrontCamera)
            putExtra("avatar_tone", spinnerAvatarTone.selectedItemPosition)
            putExtra("height_cm", heightCm)
        }
        startActivity(intent)
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            openCamera(pendingGarmentIndex)
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }
}
