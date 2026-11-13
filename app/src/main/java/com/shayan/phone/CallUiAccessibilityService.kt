package com.shayan.phone

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Compatibility diagnostics only.
 * Observes a small allowlist of phone UI packages without inspecting content.
 */
class CallUiAccessibilityService : AccessibilityService() {
    private val allowedPackages = setOf(
        "com.android.phone",
        "com.android.incallui",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.huawei.contacts",
        "com.huawei.phone"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        PhoneErrorLog.add(this, "ACCESSIBILITY_CONNECTED", "Call UI diagnostic service enabled")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in allowedPackages) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        // Deliberately do not inspect event.text, source nodes, or window content.
        PhoneErrorLog.add(
            this,
            "ACCESSIBILITY_UI_EVENT",
            "package=" + packageName + "; eventType=" + event.eventType
        )
    }

    override fun onInterrupt() {
        PhoneErrorLog.add(this, "ACCESSIBILITY_INTERRUPTED", "Diagnostic service interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        PhoneErrorLog.add(this, "ACCESSIBILITY_DISCONNECTED", "Diagnostic service disconnected")
        return super.onUnbind(intent)
    }
}
