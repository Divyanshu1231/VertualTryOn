package com.example.virtualtryon

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter

class TFLiteBodyHelper(private val context: Context) {
    private var interpreter: Interpreter? = null

    fun isReady(): Boolean = interpreter != null

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    fun logAvailability() {
        Log.d("TFLiteBodyHelper", "TensorFlow Lite dependency ready. Add a .tflite body model in assets to enable custom inference.")
    }
}
