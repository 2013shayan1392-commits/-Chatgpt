package com.shayan.apphub.killapps

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

class CleanKillAppsAccessibilityService : AccessibilityService() {
    private enum class Stage { OPENING_INFO, WAITING_FOR_CONFIRMATION, WAITING_FOR_RESULT }

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<String>()
    private val labels = LinkedHashMap<String, String>()
    private val failures = LinkedHashSet<String>()

    private var activePackage: String? = null
    private var activeLabel: String = ""
    private var stage = Stage.OPENING_INFO
    private var stopButtonClicked = false
    private var confirmationAccepted = false
    private var resultChecks = 0
    private var completed = 0
    private var total = 0
    private var running = false
    private var showProgress = false
    private var onFinished: ((Set<String>) -> Unit)? = null
    private var timeoutRunnable: Runnable? = null
    private var resultRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!running || activePackage == null || event == null) return
        if (
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            handler.post { inspectActiveWindow() }
        }
    }

    override fun onInterrupt() {
        handler.post { cancelOperation("سرویس دسترس‌پذیری متوقف شد.") }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        CleanKillAppsProgressWindow.dismiss()
        super.onDestroy()
    }

    private fun beginNext() {
        clearTimers()
        if (queue.isEmpty()) {
            finishOperation()
            return
        }

        activePackage = queue.removeFirst()
        val nextPackage = activePackage ?: return
        activeLabel = labels[nextPackage] ?: nextPackage
        stage = Stage.OPENING_INFO
        stopButtonClicked = false
        confirmationAccepted = false
        resultChecks = 0
        publish("بازکردن تنظیمات برنامه: " + activeLabel)

        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:" + activePackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            finishActive(false, "صفحهٔ اطلاعات برنامه باز نشد.")
            return
        }

        timeoutRunnable = Runnable {
            if (activePackage != null) {
                finishActive(false, "دکمهٔ توقف در صفحهٔ اطلاعات پیدا نشد یا سیستم اجازه نداد.")
            }
        }
        handler.postDelayed(timeoutRunnable!!, 9000)
    }

    private fun inspectActiveWindow() {
        if (!running || activePackage == null) return
        val root = rootInActiveWindow ?: return
        val rootPackage = root.packageName?.toString().orEmpty()
        if (rootPackage == packageName) return

        val text = collectText(root).joinToString(" ").lowercase()
        if (isConfirmationPrompt(text)) {
            val confirm = findNode(root) { node ->
                val value = nodeText(node)
                value in setOf(
                    "force stop", "ok", "yes", "stop", "توقف اجباری",
                    "توقف اجباری", "تأیید", "تایید", "بله", "موافق"
                )
            }
            if (confirm != null && !confirmationAccepted && clickNode(confirm)) {
                confirmationAccepted = true
                stage = Stage.WAITING_FOR_RESULT
                resultChecks = 0
                publish("تأیید توقف: " + activeLabel)
                scheduleResultCheck(900)
            } else if (confirmationAccepted) {
                scheduleResultCheck(700)
            }
            return
        }

        val stopNode = findNode(root) { node -> isForceStopButton(node) }
        if (stopNode != null) {
            if (!stopNode.isEnabled) {
                finishActive(true, "دکمهٔ توقف غیرفعال شد؛ درخواست پذیرفته شد.")
                return
            }
            if (!stopButtonClicked) {
                if (clickNode(stopNode)) {
                    stopButtonClicked = true
                    stage = Stage.WAITING_FOR_CONFIRMATION
                    publish("درخواست توقف برای " + activeLabel + " ثبت شد.")
                    scheduleResultCheck(1300)
                }
            }
            return
        }

        if (stage == Stage.WAITING_FOR_RESULT && stopButtonClicked) {
            scheduleResultCheck(1300)
        }
    }

    private fun isForceStopButton(node: AccessibilityNodeInfo): Boolean {
        val text = nodeText(node)
        val viewId = try {
            node.viewIdResourceName.orEmpty().lowercase()
        } catch (_: Exception) {
            ""
        }
        val labelMatch = text in setOf(
            "force stop",
            "stop",
            "توقف اجباری",
            "توقف اجباری",
            "متوقف کردن اجباری",
            "توقف",
            "zorla durdur",
            "forzar detención",
            "forçar parada",
            "erzwingen beenden",
            "arrêter de force"
        )
        val idMatch = viewId.contains("force_stop") || viewId.contains("force-stop")
        return labelMatch || idMatch
    }

    private fun isConfirmationPrompt(text: String): Boolean {
        return text.contains("do you want to force stop") ||
            text.contains("force stop this app") ||
            text.contains("force stop?") ||
            text.contains("if you force stop") ||
            text.contains("force stop the app") ||
            text.contains("ممکن است برنامه") ||
            text.contains("می‌خواهید این برنامه را متوقف") ||
            text.contains("آیا می‌خواهید") ||
            text.contains("توقف اجباری این برنامه")
    }

    private fun scheduleResultCheck(delay: Long) {
        resultRunnable?.let(handler::removeCallbacks)
        resultRunnable = Runnable {
            if (!running || activePackage == null) return@Runnable

            val root = rootInActiveWindow
            if (root != null) {
                val text = collectText(root).joinToString(" ").lowercase()
                if (isConfirmationPrompt(text)) {
                    if (!confirmationAccepted) {
                        val confirm = findNode(root) { node ->
                            nodeText(node) in setOf("force stop", "ok", "yes", "stop", "توقف اجباری", "تأیید", "تایید", "بله", "موافق")
                        }
                        if (confirm != null && clickNode(confirm)) {
                            confirmationAccepted = true
                            resultChecks = 0
                        }
                    }
                    // Wait until the system confirmation sheet disappears before
                    // inspecting the underlying App info page for a disabled button.
                } else {
                    val button = findNode(root) { isForceStopButton(it) }
                    if (button != null && !button.isEnabled) {
                        finishActive(true, "وضعیت توقف " + activeLabel + " در تنظیمات تأیید شد.")
                        return@Runnable
                    }
                }
            }

            resultChecks += 1
            if (resultChecks <= 4) {
                scheduleResultCheck(750)
            } else {
                finishActive(false, "سیستم نتیجهٔ توقف " + activeLabel + " را تأیید نکرد؛ مورد ناموفق ثبت شد.")
            }
        }
        handler.postDelayed(resultRunnable!!, delay)
    }

    private fun finishActive(success: Boolean, message: String) {
        clearTimers()
        val finishedPackage = activePackage ?: return
        if (!success) failures.add(finishedPackage)
        completed = (completed + 1).coerceAtMost(total)
        activePackage = null
        stopButtonClicked = false
        confirmationAccepted = false
        resultChecks = 0
        publish(message)
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ beginNext() }, 550)
    }

    private fun finishOperation() {
        clearTimers()
        running = false
        activePackage = null
        persistFailures()
        CleanKillAppsProgressWindow.update(
            completed = total,
            total = total,
            appName = "پایان عملیات",
            messageText = "پردازش فهرست انتخاب‌شده تمام شد."
        )
        handler.postDelayed({
            CleanKillAppsProgressWindow.dismiss()
            onFinished?.invoke(failures.toSet())
            onFinished = null
            sendStatus("پایان عملیات", completed, total, true)
        }, 1000)
    }

    private fun cancelOperation(message: String) {
        if (!running) return
        clearTimers()
        queue.clear()
        activePackage = null
        running = false
        performGlobalAction(GLOBAL_ACTION_BACK)
        CleanKillAppsProgressWindow.dismiss()
        publish(message)
        persistFailures()
        onFinished?.invoke(failures.toSet())
        onFinished = null
    }

    private fun publish(message: String) {
        CleanKillAppsProgressWindow.update(
            completed = completed,
            total = total,
            appName = activeLabel.ifBlank { "KillApps" },
            messageText = message
        )
        sendStatus(message, completed, total, false)
    }

    private fun sendStatus(message: String, done: Int, count: Int, finished: Boolean) {
        val intent = Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_COMPLETED, done)
            putExtra(EXTRA_TOTAL, count)
            putExtra(EXTRA_FINISHED, finished)
        }
        sendBroadcast(intent)
    }

    private fun persistFailures() {
        getSharedPreferences("killapps_fresh_state_v1", MODE_PRIVATE)
            .edit()
            .putStringSet("failed", failures.toSet())
            .apply()
    }

    private fun clearTimers() {
        timeoutRunnable?.let(handler::removeCallbacks)
        resultRunnable?.let(handler::removeCallbacks)
        timeoutRunnable = null
        resultRunnable = null
    }

    private fun collectText(root: AccessibilityNodeInfo): List<String> {
        val values = mutableListOf<String>()
        root.text?.toString()?.takeIf { it.isNotBlank() }?.let(values::add)
        root.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(values::add)
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            values.addAll(collectText(child))
        }
        return values
    }

    private fun nodeText(node: AccessibilityNodeInfo): String {
        return (node.text?.toString() ?: node.contentDescription?.toString())
            .orEmpty()
            .trim()
            .lowercase()
    }

    private fun findNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(root)) return root
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val result = findNode(child, predicate)
            if (result != null) return result
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var candidate: AccessibilityNodeInfo? = node
        repeat(7) {
            val current = candidate ?: return false
            if (current.isClickable) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            candidate = current.parent
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    companion object {
        const val ACTION_STATUS = "com.shayan.apphub.killapps.ACTION_STATUS"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_COMPLETED = "completed"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_FINISHED = "finished"

        @Volatile
        private var instance: CleanKillAppsAccessibilityService? = null

        fun enqueue(
            context: Context,
            packages: List<String>,
            labels: Map<String, String>,
            showProgress: Boolean,
            onFinished: (Set<String>) -> Unit
        ): Boolean {
            val service = instance ?: return false
            if (packages.isEmpty() || service.running) return false

            service.handler.post {
                service.queue.clear()
                service.queue.addAll(packages.distinct())
                service.labels.clear()
                service.labels.putAll(labels)
                service.failures.clear()
                service.completed = 0
                service.total = service.queue.size
                service.onFinished = onFinished
                service.showProgress = showProgress
                service.running = true

                if (showProgress) {
                    val shown = CleanKillAppsProgressWindow.show(
                        context.applicationContext,
                        service.total,
                        onCancel = {
                            service.handler.post {
                                service.cancelOperation("عملیات توسط کاربر لغو شد.")
                            }
                        }
                    )
                    if (!shown) {
                        service.running = false
                        service.onFinished = null
                        return@post
                    }
                }
                service.beginNext()
            }
            return true
        }
    }
}
