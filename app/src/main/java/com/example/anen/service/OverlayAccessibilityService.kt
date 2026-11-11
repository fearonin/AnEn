package com.example.anen.service

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.anen.data.DBHelper
import com.example.anen.overlay.OverlayManager
import com.example.anen.utils.PreferencesManager

class OverlayAccessibilityService : AccessibilityService() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var overlayManager: OverlayManager
    private lateinit var dbHelper: DBHelper
    private var currentPackageName: String? = null

    companion object {
        private const val TAG = "OverlayService"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        preferencesManager = PreferencesManager(this)
        dbHelper = DBHelper(this)
        overlayManager = OverlayManager(this, dbHelper)
        Log.d(TAG, "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            if (packageName == "com.android.systemui" || packageName == applicationContext.packageName) {
                return
            }

            if (currentPackageName != packageName) {
                currentPackageName = packageName
                evaluateOverlayState(packageName)
            }
        }
    }

    private fun evaluateOverlayState(activePackage: String) {
        val targetPackage = preferencesManager.getTargetPackage()
        val isServiceEnabled = preferencesManager.isServiceEnabled()

        if (isServiceEnabled && activePackage == targetPackage) {
            overlayManager.showOverlay { endX, endY ->
                Log.d(TAG, "Swipe up detected at X: $endX, Y: $endY. Opening animated input window.")

                // Pass the coordinates to the input window
                overlayManager.showInputWindow(endX, endY) {
                    Log.d(TAG, "Input window dismissed. Restoring swipe area.")
                    if (currentPackageName == targetPackage && preferencesManager.isServiceEnabled()) {
                        // Re-attach the swipe listener
                        overlayManager.showOverlay { x, y ->
                            overlayManager.showInputWindow(x, y) { /* Recursive callback logic */ }
                        }
                    }
                }
            }
        } else {
            overlayManager.hideOverlay()
        }
    }

    // Handle screen rotation to update the swipe area orientation
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Configuration changed. Updating overlay.")

        val targetPackage = preferencesManager.getTargetPackage()
        if (currentPackageName == targetPackage && preferencesManager.isServiceEnabled()) {
            overlayManager.hideOverlay()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                overlayManager.showOverlay { x, y ->
                    overlayManager.showInputWindow(x, y) { /* Restore logic */ }
                }
            }, 500)
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