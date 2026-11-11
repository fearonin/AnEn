package com.example.anen.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.anen.overlay.OverlayManager
import com.example.anen.utils.PreferencesManager

/**
 * Accessibility Service responsible for detecting the currently active application.
 * This allows the app to show the invisible swipe area only over the user's chosen target app.
 */
class OverlayAccessibilityService : AccessibilityService() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var overlayManager: OverlayManager
    private var currentPackageName: String? = null

    companion object {
        private const val TAG = "OverlayService"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        preferencesManager = PreferencesManager(this)
        overlayManager = OverlayManager(this)
        Log.d(TAG, "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Listen only for window state changes (switching apps)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            // Ignore system UI and our own app to prevent loops
            if (packageName == "com.android.systemui" || packageName == applicationContext.packageName) {
                return
            }

            // Only act if the active window changed
            if (currentPackageName != packageName) {
                currentPackageName = packageName
                evaluateOverlayState(packageName)
            }
        }
    }

    /**
     * Determines if the overlay should be shown based on the active package.
     */
    private fun evaluateOverlayState(activePackage: String) {
        val targetPackage = preferencesManager.getTargetPackage()
        val isServiceEnabled = preferencesManager.isServiceEnabled()

        if (isServiceEnabled && activePackage == targetPackage) {
            // We are in the target app and the service is enabled -> Show overlay
            overlayManager.showOverlay {
                // TODO: In Stage 5, we will open the input window here.
                Log.d(TAG, "Swipe up detected in target app!")
            }
        } else {
            // We are in another app or service is disabled -> Hide overlay
            overlayManager.hideOverlay()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
        overlayManager.hideOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayManager.isInitialized) {
            overlayManager.hideOverlay()
        }
        Log.d(TAG, "Accessibility Service Destroyed")
    }
}