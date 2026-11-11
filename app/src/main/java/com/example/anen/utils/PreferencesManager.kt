package com.example.anen.utils

import android.content.Context
import androidx.core.content.edit

/**
 * Centralized manager for handling application preferences.
 * This ensures consistency across MainActivity and AccessibilityService.
 */
class PreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences("AnEnPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TARGET_PACKAGE = "target_package"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
    }

    /**
     * Saves the user's target package name (the app where overlay is allowed).
     */
    fun setTargetPackage(packageName: String) {
        prefs.edit { putString(KEY_TARGET_PACKAGE, packageName) }
    }

    /**
     * Retrieves the selected target package name. Returns null if not set.
     */
    fun getTargetPackage(): String? {
        return prefs.getString(KEY_TARGET_PACKAGE, null)
    }

    /**
     * Saves the global toggle state (Service Enabled / Disabled).
     */
    fun setServiceEnabled(isEnabled: Boolean) {
        prefs.edit { putBoolean(KEY_SERVICE_ENABLED, isEnabled) }
    }

    /**
     * Checks if the service is globally enabled by the user.
     */
    fun isServiceEnabled(): Boolean {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false)
    }

    private val KEY_THEME = "app_theme"

    fun setTheme(theme: String) {
        prefs.edit { putString(KEY_THEME, theme) }
    }

    fun getTheme(): String {
        return prefs.getString(KEY_THEME, "monochrome") ?: "monochrome"
    }
}