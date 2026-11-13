package com.shayan.huaweiprobe

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var runButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private var connection: ServiceConnection? = null
    private var isBound = false
    private val servicePackage = "com.huawei.phone.recorder"
    private val serviceAction = "com.android.phone.action.voiceRecoderService"
    private val requiredPermission = "com.huawei.smartkey.permission.VOICE_RECORDER"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
        }
        val title = TextView(this).apply {
            text = "آزمایش سازگاری ضبط هواوی"
            textSize = 22f
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "این ابزار فقط وضعیت سرویس و مجوز اتصال را بررسی می‌کند؛ هیچ تماسی ضبط نمی‌شود."
            textSize = 14f
            setPadding(0, 16, 0, 16)
        }
        runButton = Button(this).apply {
            text = "شروع بررسی"
            setOnClickListener { runDiagnostics() }
        }
        output = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
            text = "آمادهٔ بررسی روی گوشی هواوی."
            setPadding(0, 18, 0, 0)
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(runButton)
        root.addView(ScrollView(this).apply { addView(output) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun log(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        output.append("\n[$stamp] $message")
    }

    private fun runDiagnostics() {
        if (isBound) {
            log("بررسی قبلی هنوز در حال اجراست.")
            return
        }
        output.text = "گزارش بررسی"
        runButton.isEnabled = false
        log("مدل: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        log("اندروید: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        log("ساخت سیستم: ${android.os.Build.DISPLAY}")

        val packageInfo = try {
            packageManager.getPackageInfo(servicePackage, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        if (packageInfo == null) {
            log("نتیجه: بستهٔ ضبط هواوی پیدا نشد: $servicePackage")
            finishReport()
            return
        }
        @Suppress("DEPRECATION")
        log("بسته پیدا شد؛ نسخه: ${packageInfo.versionName ?: "نامشخص"}")

        val permissionGranted = checkSelfPermission(requiredPermission) == PackageManager.PERMISSION_GRANTED
        log("مجوز اختصاصی هواوی: " + if (permissionGranted) "اعطا شده" else "اعطا نشده")

        val query = Intent(serviceAction).setPackage(servicePackage)
        val resolved = packageManager.resolveService(query, PackageManager.MATCH_DISABLED_COMPONENTS)
        if (resolved == null) {
            log("سرویس با اکشن مورد انتظار resolve نشد.")
            finishReport()
            return
        }
        val info = resolved.serviceInfo
        log("سرویس پیدا شد: ${info.name}")
        log("exported=${info.exported}; permission=${info.permission ?: "ندارد"}")
        log("process=${info.processName ?: "پیش‌فرض"}")

        if (!info.exported) {
            log("اتصال از اپ دیگر طبق مشخصات سرویس مجاز نیست.")
            finishReport()
            return
        }

        log("در حال آزمون اتصال بدون فراخوانی فرمان ضبط…")
        val component = ComponentName(info.packageName, info.name)
        val bindIntent = Intent(serviceAction).setComponent(component)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                handler.removeCallbacksAndMessages(this)
                log("اتصال Binder برقرار شد.")
                val descriptor = runCatching { binder.interfaceDescriptor }.getOrNull()
                log("Binder descriptor: ${descriptor ?: "قابل خواندن نیست"}")
                log("Binder alive: ${binder.isBinderAlive}")
                safelyUnbind(this)
                log("هیچ متد ضبط یا دریافت صدایی فراخوانی نشد.")
                finishReport()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                log("سرویس قطع شد.")
            }

            override fun onBindingDied(name: ComponentName) {
                log("اتصال Binder از طرف سیستم از بین رفت.")
                safelyUnbind(this)
                finishReport()
            }

            override fun onNullBinding(name: ComponentName) {
                log("سرویس اتصال خالی برگرداند.")
                safelyUnbind(this)
                finishReport()
            }
        }
        connection = conn
        val accepted = try {
            bindService(bindIntent, conn, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            log("اتصال رد شد: SecurityException")
            finishReport()
            return
        } catch (e: Exception) {
            log("خطای اتصال: ${e.javaClass.simpleName}")
            finishReport()
            return
        }
        if (!accepted) {
            log("سیستم درخواست اتصال را نپذیرفت.")
            finishReport()
            return
        }
        isBound = true
        handler.postDelayed({
            if (isBound) {
                log("مهلت اتصال تمام شد؛ پاسخی از سرویس نرسید.")
                connection?.let { safelyUnbind(it) }
                finishReport()
            }
        }, 7000)
    }

    private fun safelyUnbind(conn: ServiceConnection) {
        if (!isBound) return
        runCatching { unbindService(conn) }
        isBound = false
        connection = null
    }

    private fun finishReport() {
        runOnUiThread {
            runButton.isEnabled = true
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        connection?.let { safelyUnbind(it) }
        super.onDestroy()
    }
}
