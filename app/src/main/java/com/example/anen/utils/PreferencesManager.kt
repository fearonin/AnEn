package com.example.anen.utils

import android.content.Context
import androidx.core.content.edit

class PreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences("AnEnPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TARGET_PACKAGES = "target_packages"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_THEME = "app_theme"
        private const val KEY_INVOCATION_METHOD = "invocation_method"
    }

    fun setTargetPackages(packageNames: Set<String>) {
        prefs.edit { putStringSet(KEY_TARGET_PACKAGES, packageNames) }
    }

    fun getTargetPackages(): Set<String> {
        return prefs.getStringSet(KEY_TARGET_PACKAGES, emptySet()) ?: emptySet()
    }

    fun setServiceEnabled(isEnabled: Boolean) {
        prefs.edit { putBoolean(KEY_SERVICE_ENABLED, isEnabled) }
    }

    fun isServiceEnabled(): Boolean {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false)
    }

    fun setTheme(theme: String) {
        prefs.edit { putString(KEY_THEME, theme) }
    }

    fun getTheme(): String {
        return prefs.getString(KEY_THEME, "monochrome") ?: "monochrome"
    }

    fun setInvocationMethod(method: String) {
        prefs.edit { putString(KEY_INVOCATION_METHOD, method) }
    }

    fun getInvocationMethod(): String {
        return prefs.getString(KEY_INVOCATION_METHOD, "swipe") ?: "swipe"
    }
}