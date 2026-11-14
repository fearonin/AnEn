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
import android.widget.Toast
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
                Handler(Looper.getMainLooper()).post {
                    // FIX: Block if no languages exist
                    if (dbHelper.getAllLanguages().isEmpty()) {
                        Toast.makeText(context, "Add at least one language in the app", Toast.LENGTH_SHORT).show()
                        return@post
                    }
                    val langId = preferencesManager.getDefaultLanguageId()
                    val srcId = preferencesManager.getDefaultSourceId()
                    overlayManager.showInputWindow(0f, 0f, langId, srcId) { }
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
        // Безопасная регистрация для всех версий Android (убирает ошибку линтера)
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            overlayReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

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

        if (isTargetApp && invocationMethod == "swipe") {
            // FIX: Block swipe overlay if no languages exist
            if (dbHelper.getAllLanguages().isEmpty()) {
                overlayManager.hideOverlay()
                return
            }

            overlayManager.showOverlay { endX, endY ->
                val langId = preferencesManager.getDefaultLanguageId()
                val srcId = preferencesManager.getDefaultSourceId()
                overlayManager.showInputWindow(endX, endY, langId, srcId) {
                    if (currentPackageName == activePackage && preferencesManager.isServiceEnabled() && preferencesManager.getInvocationMethod() == "swipe") {
                        overlayManager.showOverlay { x, y ->
                            overlayManager.showInputWindow(x, y, langId, srcId) { }
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

        // FIX: Block back button overlay if no languages exist
        if (dbHelper.getAllLanguages().isEmpty()) {
            return super.onKeyEvent(event)
        }

        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    isBackButtonTriggered = false
                    backButtonRunnable = Runnable {
                        isBackButtonTriggered = true
                        Handler(Looper.getMainLooper()).post {
                            val langId = preferencesManager.getDefaultLanguageId()
                            val srcId = preferencesManager.getDefaultSourceId()
                            overlayManager.showInputWindow(0f, 0f, langId, srcId) { }
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
            if (dbHelper.getAllLanguages().isEmpty()) return // FIX: Block rotation re-draw

            overlayManager.hideOverlay()
            Handler(Looper.getMainLooper()).postDelayed({
                overlayManager.showOverlay { x, y ->
                    val langId = preferencesManager.getDefaultLanguageId()
                    val srcId = preferencesManager.getDefaultSourceId()
                    overlayManager.showInputWindow(x, y, langId, srcId) { }
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