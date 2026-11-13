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
import android.provider.Settings
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

        val accessibilityButton = Button(this).apply {
            text = "فعال‌سازی مشاهده‌گر رابط تماس"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val uiReportButton = Button(this).apply {
            text = "نمایش کنترل‌های ضبط شناسایی‌شده"
            setOnClickListener { showUiObservations() }
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
        root.addView(accessibilityButton)
        root.addView(uiReportButton)
        root.addView(ScrollView(this).apply { addView(output) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun log(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        output.append("\n[$stamp] $message")
    }

    @Suppress("DEPRECATION")
    private fun logPermissionDetails() {
        log("بررسی مشخصات مجوز در PackageManager…")
        val permissionInfo = try {
            packageManager.getPermissionInfo(requiredPermission, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (e: Exception) {
            log("خواندن مشخصات مجوز ناموفق بود: ${e.javaClass.simpleName}")
            null
        }

        if (permissionInfo == null) {
            log("این مجوز در PackageManager ثبت نشده یا برای این برنامه قابل مشاهده نیست.")
            return
        }

        log("مجوز توسط بستهٔ زیر تعریف شده: ${permissionInfo.packageName}")
        val protectionBase = permissionInfo.protectionLevel and 0xF
        val protectionName = when (protectionBase) {
            0 -> "normal"
            1 -> "dangerous"
            2 -> "signature"
            3 -> "signatureOrSystem (legacy)"
            else -> "unknown"
        }
        log("Protection level: $protectionName")
        log("Protection level عددی: 0x${permissionInfo.protectionLevel.toString(16)}")
        log("Permission group: ${permissionInfo.group ?: "ندارد"}")

        val installed = try {
            packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        } catch (e: Exception) {
            log("فهرست بسته‌های درخواست‌کننده خوانده نشد: ${e.javaClass.simpleName}")
            emptyList()
        }

        val requesters = installed.mapNotNull { pkg ->
            val requested = pkg.requestedPermissions ?: return@mapNotNull null
            val index = requested.indexOf(requiredPermission)
            if (index < 0) return@mapNotNull null
            val flags = pkg.requestedPermissionsFlags
            val granted = flags?.getOrNull(index)?.let {
                (it and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            } ?: false
            "${pkg.packageName} (granted=$granted)"
        }

        log("بسته‌های قابل مشاهده‌ای که این مجوز را درخواست کرده‌اند: ${requesters.size}")
        if (requesters.isEmpty()) {
            log("هیچ درخواست‌کننده‌ای در فهرست قابل مشاهده پیدا نشد.")
        } else {
            requesters.forEach { log("درخواست‌کننده: $it") }
        }

        val holders = try {
            packageManager.getPackagesHoldingPermissions(arrayOf(requiredPermission), 0)
        } catch (e: Exception) {
            log("خواندن بسته‌های دارندهٔ مجوز ناموفق بود: ${e.javaClass.simpleName}")
            emptyList()
        }
        log("بسته‌های قابل مشاهدهٔ دارای مجوز اعطاشده: ${holders.size}")
        holders.forEach { log("دارندهٔ مجوز: ${it.packageName}") }
        log("توجه: فهرست بسته‌ها ممکن است تحت محدودیت مشاهدهٔ بسته‌ها در اندروید 11 به بعد کامل نباشد.")

        log("مقایسهٔ UID و وضعیت مجوز بسته‌های مرتبط…")
        val relatedPackages = listOf(servicePackage, "com.android.incallui", "com.android.phone", "com.android.huawei.smartkey", packageName).distinct()
        val thisUid = applicationInfo.uid
        log("اپ آزمایشی: $packageName؛ UID=$thisUid؛ مجوز=" + if (checkSelfPermission(requiredPermission) == PackageManager.PERMISSION_GRANTED) "اعطا شده" else "اعطا نشده")
        relatedPackages.forEach { relatedName ->
            try {
                val related = packageManager.getPackageInfo(relatedName, PackageManager.GET_PERMISSIONS)
                val uid = related.applicationInfo?.uid ?: -1
                val requestIndex = related.requestedPermissions?.indexOf(requiredPermission) ?: -1
                val requested = requestIndex >= 0
                val relatedFlags = related.requestedPermissionsFlags
                val requestedGranted = requested && relatedFlags?.getOrNull(requestIndex)?.let {
                    (it and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                } == true
                val uidPermissionGranted = packageManager.checkPermission(requiredPermission, relatedName) == PackageManager.PERMISSION_GRANTED
                @Suppress("DEPRECATION")
                val sharedUid = related.sharedUserId ?: "ندارد"
                log("بسته: $relatedName")
                log("  UID=$uid؛ sharedUserId=$sharedUid؛ هم‌UID با اپ آزمایشی=${uid == thisUid}")
                log("  مجوز را در Manifest درخواست کرده؟ $requested؛ پرچم granted=$requestedGranted")
                log("  بررسی PackageManager برای این بسته: " + if (uidPermissionGranted) "GRANTED" else "DENIED")
            } catch (_: PackageManager.NameNotFoundException) {
                log("بستهٔ مرتبط نصب نیست یا قابل مشاهده نیست: $relatedName")
            } catch (e: Exception) {
                log("خواندن اطلاعات $relatedName ناموفق بود: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun showUiObservations() {
        output.text = "گزارش مشاهدهٔ رابط تماس"
        val prefs = getSharedPreferences("ui_probe", MODE_PRIVATE)
        val matches = prefs.getStringSet("matches", emptySet())?.toList()?.sorted().orEmpty()
        val lastScan = prefs.getLong("last_scan", 0L)
        if (lastScan > 0L) {
            log("آخرین مشاهده: " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(lastScan)))
        }
        if (matches.isEmpty()) {
            log("هنوز کنترل مرتبط با ضبط در رابط تماس هواوی شناسایی نشده است.")
            log("برای آزمایش، مشاهده‌گر را در تنظیمات دسترس‌پذیری فعال کن و سپس یک تماس آزمایشی برقرار کن.")
        } else {
            log("تعداد کنترل‌های مرتبط شناسایی‌شده: " + matches.size)
            matches.forEach { log(it) }
        }
        log("این مشاهده‌گر فقط رابط قابل‌مشاهده را می‌خواند؛ هیچ دکمه‌ای را لمس نمی‌کند و هیچ صدایی ضبط نمی‌کند.")
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
        @Suppress("DEPRECATION")
        val probeVersion = packageManager.getPackageInfo(packageName, 0).versionName
        log("نسخهٔ ابزار تشخیصی: ${probeVersion ?: "نامشخص"}")

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
        logPermissionDetails()

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
