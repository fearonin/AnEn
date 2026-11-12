package com.example.anen.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.anen.data.DBHelper
import com.example.anen.overlay.OverlayManager
import com.example.anen.utils.PreferencesManager

class OverlayAccessibilityService : AccessibilityService() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var overlayManager: OverlayManager
    private lateinit var dbHelper: DBHelper
    private var currentPackageName: String? = null

    private val backButtonHandler = Handler(Looper.getMainLooper())
    private var backButtonRunnable: Runnable? = null
    private var isBackButtonTriggered = false

    private val overlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.anen.SHOW_INPUT_OVERLAY") {
                Log.d(TAG, "Broadcast received. Showing overlay.")
                Handler(Looper.getMainLooper()).post {
                    // No target app check! Works everywhere.
                    overlayManager.showInputWindow(0f, 0f) { }
                }
            }
        }
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val LONG_PRESS_DELAY = 2000L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        preferencesManager = PreferencesManager(this)
        dbHelper = DBHelper(this)
        overlayManager = OverlayManager(this, dbHelper)

        val filter = IntentFilter("com.example.anen.SHOW_INPUT_OVERLAY")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Use RECEIVER_EXPORTED because the broadcast comes from an Activity,
            // but it's secure because we use setPackage() in TileTriggerActivity.
            registerReceiver(overlayReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(overlayReceiver, filter)
        }

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
        val targetPackages = preferencesManager.getTargetPackages()
        val isServiceEnabled = preferencesManager.isServiceEnabled()
        val invocationMethod = preferencesManager.getInvocationMethod()

        val isTargetApp = isServiceEnabled && targetPackages.contains(activePackage)

        // Swipe only works in target apps and if selected
        if (isTargetApp && invocationMethod == "swipe") {
            overlayManager.showOverlay { endX, endY ->
                overlayManager.showInputWindow(endX, endY) {
                    if (currentPackageName == activePackage && preferencesManager.isServiceEnabled() && preferencesManager.getInvocationMethod() == "swipe") {
                        overlayManager.showOverlay { x, y ->
                            overlayManager.showInputWindow(x, y) { }
                        }
                    }
                }
            }
        } else {
            overlayManager.hideOverlay()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val targetPackages = preferencesManager.getTargetPackages()
        val isServiceEnabled = preferencesManager.isServiceEnabled()
        val activePackage = currentPackageName
        val invocationMethod = preferencesManager.getInvocationMethod()

        if (!isServiceEnabled || activePackage == null || !targetPackages.contains(activePackage) || invocationMethod != "back_button") {
            return super.onKeyEvent(event)
        }

        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    isBackButtonTriggered = false
                    backButtonRunnable = Runnable {
                        isBackButtonTriggered = true
                        Handler(Looper.getMainLooper()).post {
                            overlayManager.showInputWindow(0f, 0f) { }
                        }
                    }
                    backButtonHandler.postDelayed(backButtonRunnable!!, LONG_PRESS_DELAY)
                }
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                backButtonRunnable?.let { backButtonHandler.removeCallbacks(it) }
                if (isBackButtonTriggered) {
                    isBackButtonTriggered = false
                } else {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val targetPackages = preferencesManager.getTargetPackages()
        val activePackage = currentPackageName
        val invocationMethod = preferencesManager.getInvocationMethod()

        if (activePackage != null && targetPackages.contains(activePackage) && preferencesManager.isServiceEnabled() && invocationMethod == "swipe") {
            overlayManager.hideOverlay()
            Handler(Looper.getMainLooper()).postDelayed({
                overlayManager.showOverlay { x, y ->
                    overlayManager.showInputWindow(x, y) { }
                }
            }, 500)
        }
    }

    override fun onInterrupt() {
        overlayManager.hideOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayManager.isInitialized) {
            overlayManager.hideOverlay()
        }
        try {
            unregisterReceiver(overlayReceiver)
        } catch (_: Exception) { }
    }
}