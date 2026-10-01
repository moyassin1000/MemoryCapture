package com.memorycapture.app.recording

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.memorycapture.app.service.CallCaptureAccessibilityService

object CallCaptureCompatibility {
    fun isAccessibilityAssistEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val target = ComponentName(
            context,
            CallCaptureAccessibilityService::class.java,
        )

        return manager
            .getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
            )
            .any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo
                    ?: return@any false
                ComponentName(
                    serviceInfo.packageName,
                    serviceInfo.name,
                ) == target
            }
    }
}
