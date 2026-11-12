package com.example.anen.data

import android.graphics.drawable.Drawable

/**
 * Data model representing an installed application.
 */
data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable
)