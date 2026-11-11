package com.example.anen.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Manages the invisible swipe area overlay.
 * This area sits at the bottom of the screen and listens for swipe-up gestures.
 */
class OverlayManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var swipeView: View? = null
    private var isShowing = false

    companion object {
        private const val TAG = "OverlayManager"
        private const val SWIPE_THRESHOLD_DP = 60 // Minimum swipe distance to trigger
    }

    /**
     * Shows the invisible swipe area. Call this only when the target app is open.
     */
    fun showOverlay(onSwipeUpDetected: () -> Unit) {
        if (isShowing) return

        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            swipeView = View(context).apply {
                // Completely transparent background
                setBackgroundColor(Color.TRANSPARENT)
            }

            // Define layout parameters for the overlay
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                dpToPx(30), // Height: 30dp
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM // Attach to the bottom of the screen
            }

            var downY = 0f

            // Handle touch events to detect swipe up
            swipeView?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaY = downY - event.rawY
                        // If the user swiped up by more than the threshold
                        if (deltaY > dpToPx(SWIPE_THRESHOLD_DP)) {
                            onSwipeUpDetected()
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager?.addView(swipeView, params)
            isShowing = true
            Log.d(TAG, "Invisible overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay: ${e.message}")
        }
    }

    /**
     * Hides the invisible swipe area. Call this when leaving the target app.
     */
    fun hideOverlay() {
        if (!isShowing) return
        try {
            windowManager?.removeView(swipeView)
            swipeView = null
            windowManager = null
            isShowing = false
            Log.d(TAG, "Invisible overlay hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding overlay: ${e.message}")
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}