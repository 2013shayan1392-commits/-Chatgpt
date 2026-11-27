package com.shayan.apphub

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

/** Full-screen progress surface shown above Android's App info screens. */
object KillAppsProgressOverlay {
    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var ringView: RingProgressView? = null
    private var appNameView: TextView? = null
    private var statusView: TextView? = null
    private var cancelAction: (() -> Unit)? = null

    fun show(context: Context, total: Int, onCancel: () -> Unit): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        if (rootView != null) {
            cancelAction = onCancel
            update(0, total, "آماده‌سازی...", "در حال شروع...")
            return true
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(46), dp(28), dp(24))
            setBackgroundColor(0xFF303137.toInt())
            isClickable = true
            isFocusable = true
        }

        val title = TextView(context).apply {
            text = "درحال بستن برنامه‌ها..."
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        layout.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        layout.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))

        val ring = RingProgressView(context).apply { setProgress(0) }
        layout.addView(ring, LinearLayout.LayoutParams(dp(260), dp(260)))

        val appName = TextView(context).apply {
            text = "آماده‌سازی..."
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, dp(24), 0, 0)
        }
        layout.addView(appName, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val status = TextView(context).apply {
            text = "در حال شروع..."
            textSize = 14f
            setTextColor(0xFFD8D8DC.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        layout.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        layout.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))

        val cancel = TextView(context).apply {
            text = "انصراف"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                setColor(0xFF55565C.toInt())
                cornerRadius = dp(22).toFloat()
            }
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setOnClickListener { cancelAction?.invoke() }
        }
        layout.addView(cancel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(68)
        ))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        return try {
            wm.addView(layout, params)
            windowManager = wm
            rootView = layout
            ringView = ring
            appNameView = appName
            statusView = status
            cancelAction = onCancel
            update(0, total, "آماده‌سازی...", "در حال شروع...")
            true
        } catch (_: Exception) {
            dismiss()
            false
        }
    }

    fun update(completed: Int, total: Int, appName: String, status: String) {
        val percent = if (total <= 0) 0 else ((completed.coerceIn(0, total) * 100f) / total).toInt()
        ringView?.setProgress(percent)
        appNameView?.text = appName
        statusView?.text = status
    }

    fun dismiss() {
        val view = rootView
        val wm = windowManager
        if (view != null && wm != null) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {
            }
        }
        rootView = null
        ringView = null
        appNameView = null
        statusView = null
        cancelAction = null
        windowManager = null
    }

    private class RingProgressView(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF85868B.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 18f * density
            strokeCap = Paint.Cap.BUTT
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 18f * density
            strokeCap = Paint.Cap.BUTT
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 34f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        private var progress = 0

        fun setProgress(value: Int) {
            progress = value.coerceIn(0, 100)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val side = min(width, height).toFloat()
            val stroke = trackPaint.strokeWidth
            val radius = side / 2f - stroke
            val cx = width / 2f
            val cy = height / 2f
            canvas.drawCircle(cx, cy, radius, trackPaint)
            val bounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(bounds, -90f, 360f * progress / 100f, false, progressPaint)
            val baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText("$progress%", cx, baseline, textPaint)
        }
    }
}
