package com.shayan.calldiagnostics

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var reportView: TextView
    private var report = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refreshReport()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 22, 22, 22)
            setBackgroundColor(0xFF101318.toInt())
        }
        val title = TextView(this).apply {
            text = "Shayan Call Diagnostics\nبررسی قابلیت‌های ضبط تماس"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        val subtitle = TextView(this).apply {
            text = "گزارش فنی وضعیت دستگاه و مجوزها"
            textSize = 13f
            setTextColor(0xFFB8C3D1.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 18)
        }
        root.addView(subtitle)
        root.addView(actionButton("درخواست مجوزهای قابل‌درخواست") { requestNeededPermissions() })
        root.addView(actionButton("بازکردن تنظیمات دسترسی‌پذیری") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        root.addView(actionButton("بازکردن دسترسی اعلان‌ها") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        root.addView(actionButton("تنظیمات اطلاعات برنامه") { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) })
        root.addView(actionButton("بررسی دوباره") { refreshReport() })
        root.addView(actionButton("کپی کل گزارش") { copyReport() })
        reportView = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFE7EDF5.toInt())
            setPadding(12, 16, 12, 24)
        }
        val scroll = ScrollView(this)
        scroll.addView(reportView)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 5 }
        }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 41)
        else Toast.makeText(this, "مجوزهای قابل‌درخواست قبلاً داده شده‌اند", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if (::reportView.isInitialized) refreshReport()
    }

    private fun refreshReport() {
        val lines = mutableListOf<String>()
        fun add(name: String, value: Any?) { lines.add("$name: $value") }
        add("اپ", "Shayan Call Diagnostics 1.0")
        add("سازنده / مدل", "${Build.MANUFACTURER} / ${Build.MODEL}")
        add("برند دستگاه", Build.BRAND)
        add("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        add("Build fingerprint", Build.FINGERPRINT)
        add("Product", Build.PRODUCT)
        add("Device", Build.DEVICE)
        add("Package", packageName)
        add("RECORD_AUDIO", granted(Manifest.permission.RECORD_AUDIO))
        add("READ_PHONE_STATE", granted(Manifest.permission.READ_PHONE_STATE))
        add("READ_CALL_LOG", granted(Manifest.permission.READ_CALL_LOG))
        if (Build.VERSION.SDK_INT >= 33) add("POST_NOTIFICATIONS", granted(Manifest.permission.POST_NOTIFICATIONS))
        add("Overlay", Settings.canDrawOverlays(this))
        add("Accessibility service فعال", isAccessibilityEnabled())
        add("Notification listener فعال", isNotificationListenerEnabled())
        add("نقش Dialer قابل‌دسترسی", roleAvailable())
        add("نقش Dialer فعلی برنامه", isDefaultDialer())
        add("AudioRecord منبع MIC", buffer(MediaRecorder.AudioSource.MIC))
        add("AudioRecord منبع VOICE_RECOGNITION", buffer(MediaRecorder.AudioSource.VOICE_RECOGNITION))
        add("AudioRecord منبع VOICE_COMMUNICATION", buffer(MediaRecorder.AudioSource.VOICE_COMMUNICATION))
        add("AudioRecord منبع VOICE_CALL", buffer(MediaRecorder.AudioSource.VOICE_CALL))
        add("CAPTURE_AUDIO_OUTPUT وضعیت دسترسی", granted("android.permission.CAPTURE_AUDIO_OUTPUT"))
        add("CAPTURE_AUDIO_OUTPUT در manifest برنامه", "عمداً درخواست نشده؛ مجوز privileged/signature است و اپ عادی نمی‌تواند آن را با Restricted Settings بگیرد.")
        add("توضیح تست صوت", "فقط حداقل بافر AudioRecord بررسی می‌شود؛ startRecording/read اجرا نمی‌شود و تماس ضبط نمی‌شود.")
        add("نتیجه", "این گزارش وضعیت مجوزها و امکان اولیه AudioRecord را نشان می‌دهد؛ اثبات ضبط دوطرفه نیست.")
        report = lines.joinToString("\n")
        if (::reportView.isInitialized) reportView.text = report
    }

    private fun granted(permission: String): String =
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) "Granted" else "Denied"

    private fun probeSource(source: Int): String {
        return try {
            val rate = 44100
            val size = AudioRecord.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
            if (size <= 0) {
                "حداقل بافر نامعتبر/پشتیبانی‌نشده ($size)"
            } else {
                val recorder = AudioRecord(source, rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, size * 2)
                val state = recorder.state
                recorder.release()
                if (state == AudioRecord.STATE_INITIALIZED) "AudioRecord ساخته و مقداردهی شد؛ بدون شروع ضبط" else "ساخت AudioRecord ناموفق؛ state=$state"
            }
        } catch (e: SecurityException) {
            "دسترسی رد شد: ${e.message}"
        } catch (e: Exception) {
            "خطا: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
    private fun isAccessibilityEnabled(): Boolean = try {
        Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(packageName) == true
    } catch (_: Exception) { false }

    private fun isNotificationListenerEnabled(): Boolean = try {
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
    } catch (_: Exception) { false }

    private fun roleAvailable(): Boolean = if (Build.VERSION.SDK_INT >= 29) {
        getSystemService(RoleManager::class.java)?.isRoleAvailable(RoleManager.ROLE_DIALER) == true
    } else true

    private fun isDefaultDialer(): Boolean = if (Build.VERSION.SDK_INT >= 29) {
        getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    } else false

    private fun copyReport() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Shayan Call Diagnostics", report))
        Toast.makeText(this, "کل گزارش کپی شد", Toast.LENGTH_SHORT).show()
    }
}
