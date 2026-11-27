package com.shayan.apphub

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Processes one Android App info page at a time, issuing Force stop only once. */
class KillAppsAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<String>()
    private var labels: Map<String, String> = emptyMap()
    private var currentPackage: String? = null
    private var currentLabel = ""
    private var totalCount = 0
    private var completedCount = 0
    private var stopButtonClicked = false
    private var confirmationClicked = false
    private var confirmationRetries = 0
    private var onStatus: ((String) -> Unit)? = null
    private val failedPackages = linkedSetOf<String>()
    private var timeout: Runnable? = null
    private var settleRunnable: Runnable? = null
    private var isProcessing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isProcessing || currentPackage == null || event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root = rootInActiveWindow ?: return
        if (!stopButtonClicked) {
            val stopNode = findNode(root) { text ->
                text.contains("force stop", true) ||
                    text.contains("توقف اجباری", true) ||
                    text.contains("اجبار به توقف", true)
            } ?: return

            if (!stopNode.isEnabled) {
                finishCurrent("برنامهٔ $currentLabel از قبل متوقف است.")
                return
            }

            // Set before ACTION_CLICK to prevent duplicate clicks from subsequent events.
            stopButtonClicked = true
            val clicked = clickableAncestor(stopNode)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            if (!clicked) {
                finishCurrent("کلیک روی توقف اجباری برای $currentLabel پذیرفته نشد؛ از آن عبور شد.", failed = true)
                return
            }
            publishStatus("درخواست توقف برای $currentLabel ثبت شد؛ در انتظار نتیجه...")
            scheduleSettleCheck()
        } else {
            handleConfirmationIfVisible(root)
        }
    }

    private fun scheduleSettleCheck() {
        settleRunnable?.let(handler::removeCallbacks)
        val expectedPackage = currentPackage
        settleRunnable = Runnable {
            settleRunnable = null
            if (currentPackage != expectedPackage || !stopButtonClicked || confirmationClicked) return@Runnable
            val latestRoot = rootInActiveWindow
            if (latestRoot != null && hasConfirmationPrompt(latestRoot)) {
                handleConfirmationIfVisible(latestRoot)
            } else {
                // The system may show a short spinner/disabled state; never click again.
                finishCurrent("درخواست توقف برای $currentLabel انجام شد.")
            }
        }
        handler.postDelayed(settleRunnable!!, 1400)
    }

    private fun handleConfirmationIfVisible(root: AccessibilityNodeInfo) {
        if (!hasConfirmationPrompt(root) || confirmationClicked) return
        settleRunnable?.let(handler::removeCallbacks)
        settleRunnable = null
        val positive = findNode(root) { text ->
            text.equals("ok", true) ||
                text.equals("force stop", true) ||
                text.equals("توقف اجباری", true) ||
                text.equals("تأیید", true) ||
                text.equals("تایید", true) ||
                text.equals("موافق", true)
        }

        if (positive != null &&
            clickableAncestor(positive)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        ) {
            confirmationClicked = true
            publishStatus("تأیید توقف $currentLabel انجام شد.")
            handler.postDelayed({ finishCurrent("توقف $currentLabel تأیید شد.") }, 750)
        } else if (confirmationRetries++ < 10) {
            handler.postDelayed({
                if (currentPackage != null && !confirmationClicked) {
                    rootInActiveWindow?.let(::handleConfirmationIfVisible)
                }
            }, 350)
        }
    }

    private fun hasConfirmationPrompt(root: AccessibilityNodeInfo): Boolean {
        val text = collectText(root).joinToString(" ").lowercase()
        return text.contains("do you want to force stop") ||
            text.contains("force stop this app") ||
            text.contains("force stop?") ||
            text.contains("if you force stop") ||
            text.contains("this app may misbehave") ||
            text.contains("ممکن است برنامه") ||
            text.contains("می‌خواهید این برنامه را متوقف") ||
            text.contains("آیا می‌خواهید") ||
            text.contains("اجبار به توقف این برنامه")
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
        repeat(6) {
            val candidate = current ?: return null
            if (candidate.isClickable) return candidate
            current = candidate.parent
        }
        return node
    }

    private fun publishStatus(message: String) {
        onStatus?.invoke(message)
        KillAppsProgressOverlay.update(
            completed = completedCount,
            total = totalCount,
            appName = currentLabel.ifBlank { "در حال آماده‌سازی..." },
            status = message
        )
    }

    private fun finishCurrent(message: String, failed: Boolean = false) {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        settleRunnable?.let(handler::removeCallbacks)
        settleRunnable = null
        val finishedPackage = currentPackage ?: return
        if (failed) {
            failedPackages.add(finishedPackage)
            getSharedPreferences("killapps_ui_preferences", MODE_PRIVATE)
                .edit()
                .putStringSet("failed_packages", failedPackages.toSet())
                .apply()
        }
        onStatus?.invoke(message)
        currentPackage = null
        completedCount = (completedCount + 1).coerceAtMost(totalCount)
        stopButtonClicked = false
        confirmationClicked = false
        confirmationRetries = 0
        KillAppsProgressOverlay.update(
            completed = completedCount,
            total = totalCount,
            appName = labels[finishedPackage] ?: finishedPackage,
            status = "پردازش " + completedCount + " از " + totalCount
        )
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ startNext() }, 450)
    }

    private fun startNext() {
        if (queue.isEmpty()) {
            currentPackage = null
            KillAppsProgressOverlay.update(
                completed = totalCount,
                total = totalCount,
                appName = "پایان عملیات",
                status = "همه موارد انتخاب‌شده پردازش شدند."
            )
            handler.postDelayed({
                isProcessing = false
                KillAppsProgressOverlay.dismiss()
                onStatus?.invoke("پایان عملیات KillApps.")
                onStatus = null
                val returnIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(returnIntent)
            }, 1200)
            return
        }

        val packageName = queue.removeFirst()
        currentPackage = packageName
        currentLabel = labels[packageName] ?: packageName
        stopButtonClicked = false
        confirmationClicked = false
        confirmationRetries = 0
        publishStatus("در حال باز کردن اطلاعات برنامه: $currentLabel")

        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            timeout?.let(handler::removeCallbacks)
            timeout = Runnable {
                if (currentPackage == packageName) {
                    finishCurrent("برای $currentLabel دکمهٔ توقف پیدا نشد یا سیستم اجازه نداد؛ از آن عبور شد.", failed = true)
                }
            }
            handler.postDelayed(timeout!!, 10000)
        } catch (_: Exception) {
            finishCurrent("باز کردن اطلاعات $currentLabel ناموفق بود؛ از آن عبور شد.", failed = true)
        }
    }

    private fun cancelOperation() {
        timeout?.let(handler::removeCallbacks)
        settleRunnable?.let(handler::removeCallbacks)
        timeout = null
        settleRunnable = null
        queue.clear()
        currentPackage = null
        isProcessing = false
        onStatus?.invoke("عملیات KillApps لغو شد.")
        onStatus = null
        KillAppsProgressOverlay.dismiss()
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({
            val returnIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(returnIntent)
        }, 300)
    }

    override fun onInterrupt() {
        if (isProcessing) cancelOperation()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        KillAppsProgressOverlay.dismiss()
        super.onDestroy()
    }

    companion object {
        @Volatile private var instance: KillAppsAccessibilityService? = null

        fun start(
            context: Context,
            packages: List<String>,
            appLabels: Map<String, String>,
            showProgressWindow: Boolean,
            status: (String) -> Unit
        ): Boolean {
            val service = instance ?: return false
            if (packages.isEmpty() || service.isProcessing) return false
            if (showProgressWindow && !Settings.canDrawOverlays(context)) return false
            service.handler.post {
                service.queue.clear()
                service.queue.addAll(packages.distinct())
                service.labels = appLabels
                service.totalCount = service.queue.size
                service.completedCount = 0
                service.currentPackage = null
                service.currentLabel = ""
                service.stopButtonClicked = false
                service.confirmationClicked = false
                service.confirmationRetries = 0
                service.failedPackages.clear()
                service.getSharedPreferences("killapps_ui_preferences", MODE_PRIVATE)
                    .edit().putStringSet("failed_packages", emptySet()).apply()
                service.onStatus = status
                service.isProcessing = true
                val shown = if (showProgressWindow) {
                    KillAppsProgressOverlay.show(
                        service,
                        service.totalCount,
                        onCancel = { service.handler.post { service.cancelOperation() } }
                    )
                } else {
                    true
                }
                if (!shown) {
                    service.isProcessing = false
                    service.onStatus?.invoke("صفحهٔ پیشرفت روی سایر برنامه‌ها نمایش داده نشد؛ مجوز نمایش را بررسی کن.")
                    service.onStatus = null
                    return@post
                }
                service.startNext()
            }
            return true
        }
    }
}
