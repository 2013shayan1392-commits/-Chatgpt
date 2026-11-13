package com.shayan.huaweiprobe

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CallUiObserverService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() != TARGET_PACKAGE) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root = rootInActiveWindow ?: return
        try {
            val matches = linkedSetOf<String>()
            scan(root, matches, 0, intArrayOf(0))
            if (matches.isNotEmpty()) {
                val prefs = getSharedPreferences("ui_probe", MODE_PRIVATE)
                val saved = prefs.getStringSet("matches", emptySet())?.toMutableSet()
                    ?: mutableSetOf()
                saved.addAll(matches)
                while (saved.size > MAX_SAVED_MATCHES) {
                    saved.remove(saved.first())
                }
                prefs.edit()
                    .putStringSet("matches", saved)
                    .putLong("last_scan", System.currentTimeMillis())
                    .apply()
            }
        } finally {
            root.recycle()
        }
    }

    private fun scan(
        node: AccessibilityNodeInfo,
        matches: MutableSet<String>,
        depth: Int,
        visited: IntArray
    ) {
        if (depth > MAX_DEPTH || visited[0] >= MAX_NODES) return
        visited[0]++

        val viewId = node.viewIdResourceName.orEmpty()
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        val searchable = (viewId + " " + text + " " + description).lowercase()
        val isRecordControl = searchable.contains("record") ||
            searchable.contains("recorder") ||
            searchable.contains("ضبط")

        if (isRecordControl) {
            // Never persist unrelated visible text (such as contact names or phone numbers).
            val safeLabel = when {
                text.contains("record", ignoreCase = true) || text.contains("ضبط", ignoreCase = true) -> text
                description.contains("record", ignoreCase = true) || description.contains("ضبط", ignoreCase = true) -> description
                else -> "برچسب متنی ندارد"
            }
            val clickable = node.isClickable
            val enabled = node.isEnabled
            val className = node.className?.toString() ?: "نامشخص"
            matches.add(
                "شناسه=" + viewId.ifBlank { "نامشخص" } +
                    "؛ برچسب=" + safeLabel +
                    "؛ کلاس=" + className +
                    "؛ قابل‌کلیک=" + clickable +
                    "؛ فعال=" + enabled
            )
        }

        for (i in 0 until node.childCount) {
            if (visited[0] >= MAX_NODES) break
            val child = node.getChild(i) ?: continue
            try {
                scan(child, matches, depth + 1, visited)
            } finally {
                child.recycle()
            }
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TARGET_PACKAGE = "com.android.incallui"
        private const val MAX_DEPTH = 12
        private const val MAX_NODES = 250
        private const val MAX_SAVED_MATCHES = 30
    }
}
