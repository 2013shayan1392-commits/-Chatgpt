package com.shayan.apphub

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.provider.Settings

/**
 * Uses Android's own App info screen to request Force stop for each user-selected package.
 * Android/OEM confirmation dialogs remain in control; protected packages may refuse the action.
 */
class KillAppsAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<String>()
    private var currentPackage: String? = null
    private var stopButtonClicked = false
    private var confirmationCheckScheduled = false
    private var onStatus: ((String) -> Unit)? = null
    private var timeout: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (currentPackage == null || event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root = rootInActiveWindow ?: return
        if (!stopButtonClicked) {
            val stopNode = findNode(root) { text ->
                text.contains("force stop", true) ||
                    text.contains("توقف اجباری", true) ||
                    text.contains("اجبار به توقف", true)
            }
            if (stopNode != null && stopNode.isEnabled) {
                val target = clickableAncestor(stopNode)
                if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    stopButtonClicked = true
                    onStatus?.invoke("درخواست توقف برای $currentPackage؛ بررسی پنجرهٔ تأیید...")
                    handler.postDelayed({ handleConfirmation() }, 350)
                }
            }
        } else {
            handleConfirmation()
        }
    }

    private fun handleConfirmation() {
        if (currentPackage == null) return
        val root = rootInActiveWindow ?: return
        val allText = collectText(root).joinToString(" ").lowercase()
        val confirmationVisible =
            allText.contains("force stop") ||
            allText.contains("توقف اجباری") ||
            allText.contains("اجبار به توقف") ||
            allText.contains("می‌خواهید متوقف") ||
            allText.contains("توقف داده شود")

        if (confirmationVisible) {
            val positive = findNode(root) { text ->
                text.equals("ok", true) ||
                    text.equals("تأیید", true) ||
                    text.equals("تایید", true) ||
                    text.equals("موافق", true) ||
                    text.equals("اجبار به توقف", true)
            }
            if (positive != null && clickableAncestor(positive)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                finishCurrent("درخواست توقف برای $currentPackage تأیید شد.")
            }
        } else if (!confirmationCheckScheduled) {
            // Some Android builds stop immediately without a confirmation dialog.
            val expectedPackage = currentPackage
            confirmationCheckScheduled = true
            handler.postDelayed({
                confirmationCheckScheduled = false
                if (currentPackage == expectedPackage && stopButtonClicked) {
                    val latestRoot = rootInActiveWindow
                    val latestText = latestRoot?.let { collectText(it).joinToString(" ").lowercase() }.orEmpty()
                    val dialogStillVisible = latestText.contains("force stop") ||
                        latestText.contains("توقف اجباری") ||
                        latestText.contains("اجبار به توقف") ||
                        latestText.contains("می‌خواهید متوقف")
                    if (!dialogStillVisible) {
                        finishCurrent("درخواست توقف برای $expectedPackage ارسال شد.")
                    }
                }
            }, 1200)
        }
    }

    private fun findNode(root: AccessibilityNodeInfo, predicate: (String) -> Boolean): AccessibilityNodeInfo? {
        val nodeText = root.text?.toString().orEmpty()
        val description = root.contentDescription?.toString().orEmpty()
        if (predicate(nodeText) || predicate(description)) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findNode(child, predicate)
            if (found != null) return found
        }
        return null
    }

    private fun collectText(root: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        root.text?.toString()?.let(result::add)
        root.contentDescription?.toString()?.let(result::add)
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            result.addAll(collectText(child))
        }
        return result
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(5) {
            if (current == null) return null
            if (current!!.isClickable) return current
            current = current!!.parent
        }
        return node
    }

    private fun finishCurrent(message: String) {
        timeout?.let(handler::removeCallbacks)
        onStatus?.invoke(message)
        currentPackage = null
        stopButtonClicked = false
        confirmationCheckScheduled = false
        handler.postDelayed({ startNext() }, 500)
    }

    private fun startNext() {
        if (queue.isEmpty()) {
            val callback = onStatus
            onStatus = null
            callback?.invoke("پایان عملیات دسترس‌پذیری KillApps.")
            return
        }
        val packageName = queue.removeFirst()
        currentPackage = packageName
        stopButtonClicked = false
        confirmationCheckScheduled = false
        onStatus?.invoke("در حال باز کردن اطلاعات برنامه: $packageName")
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            timeout?.let(handler::removeCallbacks)
            timeout = Runnable {
                if (currentPackage == packageName) {
                    finishCurrent("برای $packageName دکمهٔ توقف پیدا نشد یا سیستم اجازه نداد؛ از آن عبور شد.")
                }
            }
            handler.postDelayed(timeout!!, 5000)
        } catch (error: Exception) {
            finishCurrent("باز کردن اطلاعات $packageName ناموفق بود؛ از آن عبور شد.")
        }
    }

    override fun onInterrupt() {
        onStatus?.invoke("سرویس دسترس‌پذیری متوقف شد.")
        queue.clear()
        currentPackage = null
        onStatus = null
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        @Volatile private var instance: KillAppsAccessibilityService? = null

        fun start(context: Context, packages: List<String>, status: (String) -> Unit): Boolean {
            val service = instance ?: return false
            if (packages.isEmpty()) return false
            service.handler.post {
                service.queue.clear()
                service.queue.addAll(packages.distinct())
                service.onStatus = status
                service.startNext()
            }
            return true
        }
    }
}
