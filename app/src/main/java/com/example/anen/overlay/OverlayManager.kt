package com.example.anen.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import com.example.anen.R
import com.example.anen.data.DBHelper
import kotlin.math.abs

class OverlayManager(private val context: Context, private val dbHelper: DBHelper) {

    private var windowManager: WindowManager? = null

    private var swipeView: View? = null
    private var isSwipeShowing = false

    private var inputView: View? = null
    private var isInputShowing = false

    companion object {
        private const val TAG = "OverlayManager"
        // Reduced threshold, as the landscape view is only 30dp wide
        private const val SWIPE_THRESHOLD_DP = 20
    }

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    // ==========================================
    // --- SMART INVISIBLE SWIPE AREA ---
    // ==========================================

    fun showOverlay(onSwipeUpDetected: () -> Unit) {
        if (isSwipeShowing || isInputShowing) return

        try {
            swipeView = View(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
            }

            // Use configuration orientation for more reliable detection
            val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            var gravity = Gravity.BOTTOM
            var width = WindowManager.LayoutParams.MATCH_PARENT
            var height = dpToPx(30)

            if (isLandscape) {
                // For simplicity, attach to RIGHT edge in landscape.
                // (You can add logic to detect ROTATION_270 for LEFT edge if needed)
                gravity = Gravity.RIGHT
                width = dpToPx(30)
                height = WindowManager.LayoutParams.MATCH_PARENT
            }

            val params = WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                this.gravity = gravity
            }

            var downX = 0f
            var downY = 0f

            swipeView?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Use local coordinates (event.x, event.y) instead of rawX/rawY
                        downX = event.x
                        downY = event.y
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaX = event.x - downX
                        val deltaY = event.y - downY
                        val threshold = dpToPx(SWIPE_THRESHOLD_DP)

                        var isSwipeDetected = false

                        if (gravity == Gravity.BOTTOM) {
                            // Swipe up (Y decreases)
                            if (downY - event.y > threshold) isSwipeDetected = true
                        } else if (gravity == Gravity.RIGHT) {
                            // Swipe left (X decreases)
                            if (downX - event.x > threshold) isSwipeDetected = true
                        } else if (gravity == Gravity.LEFT) {
                            // Swipe right (X increases)
                            if (event.x - downX > threshold) isSwipeDetected = true
                        }

                        if (isSwipeDetected) onSwipeUpDetected()
                        true
                    }
                    else -> false
                }
            }

            windowManager?.addView(swipeView, params)
            isSwipeShowing = true
        } catch (e: Exception) {
            Log.e(TAG, "Error showing swipe overlay: ${e.message}")
        }
    }

    fun hideOverlay() {
        if (!isSwipeShowing) return
        try {
            windowManager?.removeView(swipeView)
            swipeView = null
            isSwipeShowing = false
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding swipe overlay: ${e.message}")
        }
    }

    // ==========================================
    // --- INPUT WINDOW OVERLAY ---
    // ==========================================

    fun showInputWindow(onWindowDismissed: () -> Unit) {
        if (isInputShowing) return
        hideOverlay()

        try {
            val themedContext = ContextThemeWrapper(context, R.style.Theme_AnEn)
            inputView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_input, null)

            val rootLayout = inputView?.findViewById<FrameLayout>(R.id.rootOverlayLayout)
            val editText = inputView?.findViewById<EditText>(R.id.editTextWord)
            val btnSave = inputView?.findViewById<View>(R.id.btnSaveWord)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            }

            rootLayout?.setOnClickListener {
                hideInputWindow(saveAction = false, onWindowDismissed)
            }

            editText?.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    processAndSaveWord(editText.text.toString(), onWindowDismissed)
                    true
                } else false
            }

            btnSave?.setOnClickListener {
                processAndSaveWord(editText?.text.toString(), onWindowDismissed)
            }

            windowManager?.addView(inputView, params)
            isInputShowing = true

            editText?.postDelayed({
                editText.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }, 200)

        } catch (e: Exception) {
            Log.e(TAG, "Error showing input window: ${e.message}")
            onWindowDismissed()
        }
    }

    private fun processAndSaveWord(rawInput: String, onWindowDismissed: () -> Unit) {
        val word = rawInput.trim().lowercase()
        if (word.isEmpty()) {
            hideInputWindow(saveAction = false, onWindowDismissed)
            return
        }

        val isNewWord = dbHelper.addWord(word)
        val container = inputView?.findViewById<LinearLayout>(R.id.inputContainer)

        if (isNewWord) {
            Log.d(TAG, "New word added: $word")
            container?.setBackgroundResource(R.drawable.bg_input_gray)
        } else {
            Log.d(TAG, "Duplicate word ignored: $word")
            container?.setBackgroundResource(R.drawable.bg_input_black)
        }

        hideInputWindow(saveAction = true, onWindowDismissed)
    }

    private fun hideInputWindow(saveAction: Boolean, onWindowDismissed: () -> Unit) {
        if (!isInputShowing) return

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(inputView?.windowToken, 0)

        if (saveAction) {
            inputView?.animate()
                ?.alpha(0f)
                ?.setDuration(300)
                ?.withEndAction {
                    removeInputView()
                    onWindowDismissed()
                }
                ?.start()
        } else {
            removeInputView()
            onWindowDismissed()
        }
    }

    private fun removeInputView() {
        try {
            windowManager?.removeView(inputView)
            inputView = null
            isInputShowing = false
        } catch (e: Exception) {
            Log.e(TAG, "Error removing input view: ${e.message}")
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}