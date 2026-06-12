package com.example.virtualtryon

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

class FirebaseTracker(context: Context) {
    private val analytics: FirebaseAnalytics? = try {
        FirebaseAnalytics.getInstance(context)
    } catch (e: Exception) {
        Log.w("FirebaseTracker", "Firebase is not configured yet. Add google-services.json to enable analytics.", e)
        null
    }

    fun logTryOnEvent(eventName: String, garmentIndex: Int, camera: String) {
        analytics?.logEvent(eventName, Bundle().apply {
            putInt("garment_index", garmentIndex)
            putString("camera", camera)
        })
    }
}
