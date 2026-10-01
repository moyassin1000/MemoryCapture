package com.memorycapture.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Optional user-enabled compatibility service for call-time microphone sharing.
 *
 * This service deliberately does not inspect accessibility events or window content.
 * Its metadata restricts event delivery to MemoryCapture itself and disables
 * window-content retrieval.
 */
class CallCaptureAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
