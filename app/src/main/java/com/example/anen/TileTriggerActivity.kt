package com.example.anen

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import com.example.anen.service.OverlayAccessibilityService
import com.example.anen.utils.PreferencesManager

class TileTriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PreferencesManager(this)
        val isServiceOn = prefs.isServiceEnabled()
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = isAccessibilityServiceEnabled()

        if (!isServiceOn) {
            Toast.makeText(this, "Please enable the service in the AnEn app", Toast.LENGTH_SHORT).show()
        } else if (!hasOverlay && !hasAccessibility) {
            Toast.makeText(this, "Please grant Overlay and Accessibility permissions", Toast.LENGTH_SHORT).show()
        } else if (!hasOverlay) {
            Toast.makeText(this, "Please grant Overlay permission", Toast.LENGTH_SHORT).show()
        } else if (!hasAccessibility) {
            Toast.makeText(this, "Please grant Accessibility service", Toast.LENGTH_SHORT).show()
        } else {
            // All good, send secure broadcast
            val intent = Intent("com.example.anen.SHOW_INPUT_OVERLAY").apply {
                setPackage(packageName)
            }
            sendBroadcast(intent)
        }

        // Delay finishing slightly to ensure the Toast shows and the shade closes
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 300)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = "$packageName/${OverlayAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedService, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}