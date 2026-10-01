package com.memorycapture.app.recording

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.memorycapture.app.service.CallCaptureAccessibilityService

object CallCaptureCompatibility {
    fun isAccessibilityAssistEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val targetClassName = CallCaptureAccessibilityService::class.java.name

        return manager
            .getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
            )
            .any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo
                serviceInfo?.packageName == context.packageName &&
                    serviceInfo.name == targetClassName
            }
    }
}
