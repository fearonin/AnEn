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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.appcompat.view.ContextThemeWrapper
import com.example.anen.R
import com.example.anen.data.DBHelper
import com.example.anen.data.Language
import com.example.anen.data.Source
import com.example.anen.utils.PreferencesManager

class OverlayManager(private val context: Context, private val dbHelper: DBHelper) {

    private var windowManager: WindowManager? = null

    private var swipeView: View? = null
    private var isSwipeShowing = false

    private var inputView: View? = null
    private var isInputShowing = false

    private var originTranslationX = 0f
    private var originTranslationY = 0f

    private val preferencesManager = PreferencesManager(context)

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
                setBackgroundColor(Color.TRANSPARENT)
            }

            val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            var gravity = Gravity.BOTTOM
            var width = WindowManager.LayoutParams.MATCH_PARENT
            var height = dpToPx(60)

            if (isLandscape) {
                gravity = Gravity.END
                width = dpToPx(60)
                height = WindowManager.LayoutParams.MATCH_PARENT
            }

            val params = WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                this.gravity = gravity
            }

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
                        } else if (gravity == Gravity.END || gravity == Gravity.RIGHT) {
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
    // --- INPUT WINDOW OVERLAY ---
    // ==========================================

    fun showInputWindow(endX: Float, endY: Float, initialLangId: Int, initialSrcId: Int, onWindowDismissed: () -> Unit) {
        if (isInputShowing) return
        hideOverlay()

        try {
            val themedContext = ContextThemeWrapper(context, R.style.Theme_AnEn)
            inputView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_input, null)

            val rootLayout = inputView?.findViewById<FrameLayout>(R.id.rootOverlayLayout)
            val inputContainer = inputView?.findViewById<LinearLayout>(R.id.inputContainer)
            val editText = inputView?.findViewById<EditText>(R.id.editTextWord)
            val btnSave = inputView?.findViewById<View>(R.id.btnSaveWord)

            val spinnerLang = inputView?.findViewById<Spinner>(R.id.spinnerLanguage)
            val spinnerSrc = inputView?.findViewById<Spinner>(R.id.spinnerSource)

            // Setup Spinners
            val languages = dbHelper.getAllLanguages()
            var currentLangId = initialLangId
            var currentSrcId = initialSrcId

            if (languages.size <= 1) {
                spinnerLang?.visibility = View.GONE
            } else {
                val langNames = languages.map { it.abbreviation }.toTypedArray()
                val langAdapter = ArrayAdapter(themedContext, android.R.layout.simple_spinner_dropdown_item, langNames)
                spinnerLang?.adapter = langAdapter
                spinnerLang?.setSelection(languages.indexOfFirst { it.id == currentLangId }.coerceAtLeast(0))
            }

            fun updateSourceSpinner(langId: Int, isInitial: Boolean = false) {
                val sources = dbHelper.getSourcesForLanguage(langId)
                if (sources.size <= 1) {
                    spinnerSrc?.visibility = View.GONE
                    if (sources.isNotEmpty()) {
                        currentSrcId = sources.first().id
                        preferencesManager.setDefaultSourceId(currentSrcId)
                    }
                } else {
                    spinnerSrc?.visibility = View.VISIBLE
                    // If this is triggered by language change (not initial load), make it visible immediately
                    if (!isInitial) {
                        spinnerSrc?.alpha = 1f
                    }

                    val srcNames = sources.map { it.abbreviation }.toTypedArray()
                    val srcAdapter = ArrayAdapter(themedContext, android.R.layout.simple_spinner_dropdown_item, srcNames)
                    spinnerSrc?.adapter = srcAdapter

                    val srcIndex = sources.indexOfFirst { it.id == currentSrcId }
                    if (srcIndex == -1) {
                        currentSrcId = sources.first().id
                        spinnerSrc?.setSelection(0)
                        preferencesManager.setDefaultSourceId(currentSrcId)
                    } else {
                        spinnerSrc?.setSelection(srcIndex)
                    }
                }
            }

            updateSourceSpinner(currentLangId, isInitial = true)

            // Spinner Listeners
            spinnerLang?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedLang = languages[position]
                    currentLangId = selectedLang.id
                    preferencesManager.setDefaultLanguageId(currentLangId)

                    // Pass isInitial = false so the spinner becomes visible immediately
                    updateSourceSpinner(currentLangId, isInitial = false)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            spinnerSrc?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val sources = dbHelper.getSourcesForLanguage(currentLangId)
                    if (sources.isNotEmpty() && position < sources.size) {
                        currentSrcId = sources[position].id
                        preferencesManager.setDefaultSourceId(currentSrcId)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // Initial state for animation
            editText?.alpha = 0f
            btnSave?.alpha = 0f
            spinnerLang?.alpha = 0f
            spinnerSrc?.alpha = 0f

            val displayMetrics = context.resources.displayMetrics
            val screenCenterX = displayMetrics.widthPixels / 2f
            val screenCenterY = displayMetrics.heightPixels / 2f

            val startX = if (endX == 0f && endY == 0f) screenCenterX else endX
            val startY = if (endX == 0f && endY == 0f) screenCenterY + 200f else endY

            originTranslationX = startX - screenCenterX
            originTranslationY = startY - screenCenterY

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
                    processAndSaveWord(editText.text.toString(), currentLangId, currentSrcId, onWindowDismissed)
                    true
                } else false
            }

            btnSave?.setOnClickListener {
                processAndSaveWord(editText?.text.toString(), currentLangId, currentSrcId, onWindowDismissed)
            }

            windowManager?.addView(inputView, params)
            isInputShowing = true

            // Play appearance animation
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
                    if (spinnerLang?.visibility == View.VISIBLE) spinnerLang?.animate()?.alpha(1f)?.setDuration(200)?.start()
                    if (spinnerSrc?.visibility == View.VISIBLE) spinnerSrc?.animate()?.alpha(1f)?.setDuration(200)?.start()

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

    private fun processAndSaveWord(rawInput: String, langId: Int, srcId: Int, onWindowDismissed: () -> Unit) {
        val word = rawInput.trim().lowercase()
        if (word.isEmpty()) {
            hideInputWindow(saveAction = false, onWindowDismissed)
            return
        }

        val isNewWord = dbHelper.addWord(word, langId, srcId)
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

        val inputContainer = inputView?.findViewById<LinearLayout>(R.id.inputContainer)
        val editText = inputView?.findViewById<EditText>(R.id.editTextWord)
        val btnSave = inputView?.findViewById<View>(R.id.btnSaveWord)
        val spinnerLang = inputView?.findViewById<Spinner>(R.id.spinnerLanguage)
        val spinnerSrc = inputView?.findViewById<Spinner>(R.id.spinnerSource)

        editText?.animate()?.alpha(0f)?.setDuration(100)?.start()
        btnSave?.animate()?.alpha(0f)?.setDuration(100)?.start()
        spinnerLang?.animate()?.alpha(0f)?.setDuration(100)?.start()
        spinnerSrc?.animate()?.alpha(0f)?.setDuration(100)?.start()

        inputContainer?.animate()
            ?.scaleX(0.1f)
            ?.scaleY(0.1f)
            ?.translationX(originTranslationX)
            ?.translationY(originTranslationY)
            ?.setDuration(250)
            ?.setInterpolator(AccelerateInterpolator())
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
}