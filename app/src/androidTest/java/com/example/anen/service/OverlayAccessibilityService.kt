package com.example.anen.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility Service responsible for detecting the currently active application.
 * This allows the app to show the invisible swipe area only over the user's chosen target app.
 * Logic will be implemented in Stage 4.
 */
class OverlayAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // TODO: Track active package name in Stage 4
    }

    override fun onInterrupt() {
        // TODO: Handle service interruption
    }
}