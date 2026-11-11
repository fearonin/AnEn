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
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import com.example.anen.R
import com.example.anen.data.DBHelper

class OverlayManager(private val context: Context, private val dbHelper: DBHelper) {

    private var windowManager: WindowManager? = null

    private var swipeView: View? = null
    private var isSwipeShowing = false

    private var inputView: View? = null
    private var isInputShowing = false

    // Variables to store the origin point for the reverse animation
    private var originTranslationX = 0f
    private var originTranslationY = 0f

    companion object {
        private const val TAG = "OverlayManager"
        private const val SWIPE_THRESHOLD_DP = 20
    }

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    // ==========================================
    // --- SMART INVISIBLE SWIPE AREA ---
    // ==========================================

    fun showOverlay(onSwipeUpDetected: (Float, Float) -> Unit) {
        if (isSwipeShowing || isInputShowing) return

        try {
            swipeView = View(context).apply {
                // Для отладки можно временно сделать зону видимой:
                // setBackgroundColor(Color.argb(50, 255, 0, 0))
                setBackgroundColor(Color.TRANSPARENT)
            }

            val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val navBarHeight = getNavigationBarHeight(context)

            var gravity = Gravity.BOTTOM
            var width = WindowManager.LayoutParams.MATCH_PARENT
            var height = dpToPx(60) // Увеличили зону свайпа для удобства

            val params = WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            if (isLandscape) {
                gravity = Gravity.RIGHT
                width = dpToPx(60) // Для альбомного режима зона справа
                height = WindowManager.LayoutParams.MATCH_PARENT
                params.width = width
                params.height = height
                // Смещаем зону влево от правого края на ширину навигационной панели
                params.x = navBarHeight
            } else {
                // Портретный режим. Смещаем зону вверх от нижнего края на высоту навигационной панели
                params.y = navBarHeight
            }

            params.gravity = gravity

            var downX = 0f
            var downY = 0f

            swipeView?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val threshold = dpToPx(SWIPE_THRESHOLD_DP)
                        var isSwipeDetected = false

                        if (gravity == Gravity.BOTTOM) {
                            if (downY - event.y > threshold) isSwipeDetected = true
                        } else if (gravity == Gravity.RIGHT) {
                            if (downX - event.x > threshold) isSwipeDetected = true
                        }

                        if (isSwipeDetected) {
                            onSwipeUpDetected(event.rawX, event.rawY)
                        }
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
    // --- INPUT WINDOW OVERLAY (WITH ANIMATION) ---
    // ==========================================

    fun showInputWindow(endX: Float, endY: Float, onWindowDismissed: () -> Unit) {
        if (isInputShowing) return
        hideOverlay()

        try {
            val themedContext = ContextThemeWrapper(context, R.style.Theme_AnEn)
            inputView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_input, null)

            val rootLayout = inputView?.findViewById<FrameLayout>(R.id.rootOverlayLayout)
            val inputContainer = inputView?.findViewById<LinearLayout>(R.id.inputContainer)
            val editText = inputView?.findViewById<EditText>(R.id.editTextWord)
            val btnSave = inputView?.findViewById<View>(R.id.btnSaveWord)

            editText?.alpha = 0f
            btnSave?.alpha = 0f

            // Calculate and store the origin offset relative to the screen center
            val displayMetrics = context.resources.displayMetrics
            val screenCenterX = displayMetrics.widthPixels / 2f
            val screenCenterY = displayMetrics.heightPixels / 2f

            originTranslationX = endX - screenCenterX
            originTranslationY = endY - screenCenterY

            // Setup initial state for the appearance animation
            inputContainer?.apply {
                scaleX = 0.1f
                scaleY = 0.1f
                translationX = originTranslationX
                translationY = originTranslationY
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
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

            // PLAY APPEARANCE ANIMATION
            inputContainer?.animate()
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.translationX(0f)
                ?.translationY(0f)
                ?.setDuration(350)
                ?.setInterpolator(DecelerateInterpolator())
                ?.withEndAction {
                    editText?.animate()?.alpha(1f)?.setDuration(200)?.start()
                    btnSave?.animate()?.alpha(1f)?.setDuration(200)?.start()

                    editText?.postDelayed({
                        editText.requestFocus()
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                    }, 100)
                }
                ?.start()

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

        // Call hide with saveAction = true to trigger the reverse animation
        hideInputWindow(saveAction = true, onWindowDismissed)
    }

    private fun hideInputWindow(saveAction: Boolean, onWindowDismissed: () -> Unit) {
        if (!isInputShowing) return

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(inputView?.windowToken, 0)

        val inputContainer = inputView?.findViewById<LinearLayout>(R.id.inputContainer)
        val editText = inputView?.findViewById<EditText>(R.id.editTextWord)
        val btnSave = inputView?.findViewById<View>(R.id.btnSaveWord)

        // 1. Quickly fade out the text and button
        editText?.animate()?.alpha(0f)?.setDuration(100)?.start()
        btnSave?.animate()?.alpha(0f)?.setDuration(100)?.start()

        // 2. Reverse the animation: shrink and translate back to the origin point
        inputContainer?.animate()
            ?.scaleX(0.1f)
            ?.scaleY(0.1f)
            ?.translationX(originTranslationX)
            ?.translationY(originTranslationY)
            ?.setDuration(250) // Slightly faster than appearance to feel snappy
            ?.setInterpolator(AccelerateInterpolator()) // Accelerates as it shrinks away
            ?.withEndAction {
                removeInputView()
                onWindowDismissed()
            }
            ?.start()
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

    private fun getNavigationBarHeight(context: Context): Int {
        val resources = context.resources
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            (48 * resources.displayMetrics.density).toInt() // Fallback to 48dp
        }
    }
}