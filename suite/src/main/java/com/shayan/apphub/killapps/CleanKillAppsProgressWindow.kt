package com.shayan.apphub.killapps

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

internal object CleanKillAppsProgressWindow {
    private var manager: WindowManager? = null
    private var root: LinearLayout? = null
    private var progress: ProgressBar? = null
    private var title: TextView? = null
    private var message: TextView? = null
    private var cancelAction: (() -> Unit)? = null
    private var totalItems: Int = 0

    fun show(context: Context, total: Int, onCancel: () -> Unit): Boolean {
        if (root != null) {
            cancelAction = onCancel
            update(0, total, "آماده‌سازی", "در حال آماده‌سازی فهرست...")
            return true
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(18), dp(20), dp(16))
            background = GradientDrawable().apply {
                setColor(0xF01D2530.toInt())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0xFF526273.toInt())
            }
            elevation = dp(10).toFloat()
        }

        val heading = TextView(context).apply {
            text = "در حال پردازش برنامه‌ها"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        container.addView(
            heading,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = total.coerceAtLeast(1)
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF47C5A6.toInt())
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFF536170.toInt())
        }
        val progressParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(9)
        ).apply {
            topMargin = dp(18)
        }
        container.addView(progressBar, progressParams)

        val appLabel = TextView(context).apply {
            text = "آماده‌سازی"
            textSize = 16f
            setTextColor(0xFFEAF1F8.toInt())
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, dp(15), 0, dp(3))
        }
        container.addView(
            appLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val statusLabel = TextView(context).apply {
            text = "در حال شروع..."
            textSize = 13f
            setTextColor(0xFFB8C5D4.toInt())
            gravity = Gravity.CENTER
            maxLines = 3
        }
        container.addView(
            statusLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val cancel = Button(context).apply {
            text = "لغو عملیات"
            isAllCaps = false
            setOnClickListener { cancelAction?.invoke() }
        }
        val cancelParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(48)
        ).apply {
            topMargin = dp(14)
        }
        container.addView(cancel, cancelParams)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(18)
            windowAnimations = android.R.style.Animation_Dialog
        }

        return try {
            windowManager.addView(container, params)
            manager = windowManager
            root = container
            progress = progressBar
            title = appLabel
            message = statusLabel
            totalItems = total.coerceAtLeast(1)
            cancelAction = onCancel
            true
        } catch (_: Exception) {
            dismiss()
            false
        }
    }

    fun update(completed: Int, total: Int, appName: String, messageText: String) {
        totalItems = total.coerceAtLeast(1)
        progress?.max = totalItems
        progress?.progress = completed.coerceIn(0, totalItems)
        title?.text = appName
        message?.text = messageText + "  (" + completed.coerceIn(0, totalItems) + "/" + total + ")"
    }

    fun dismiss() {
        val oldRoot = root
        val oldManager = manager
        if (oldRoot != null && oldManager != null) {
            try {
                oldManager.removeView(oldRoot)
            } catch (_: Exception) {
            }
        }
        manager = null
        root = null
        progress = null
        title = null
        message = null
        cancelAction = null
        totalItems = 0
    }
}
