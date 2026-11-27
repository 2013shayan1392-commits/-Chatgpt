package com.shayan.apphub

import android.app.ActivityManager
import android.content.Intent
import android.content.ComponentName
import android.provider.Settings
import android.provider.Settings.Secure
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import rikka.shizuku.Shizuku

private enum class SuitePage { HOME, KILL_APPS, BREVENT }

private data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
    val isSystem: Boolean,
    val isRunning: Boolean
)

class MainActivity : ComponentActivity() {
    private var currentPage by mutableStateOf(SuitePage.HOME)
    private var installedApps by mutableStateOf<List<InstalledApp>>(emptyList())
    private var shizukuRunning by mutableStateOf(false)
    private var shizukuGranted by mutableStateOf(false)
    private var accessibilityEnabled by mutableStateOf(false)
    private var overlayPermissionGranted by mutableStateOf(false)
    private var operationMessage by mutableStateOf("")
    private var managedPackages by mutableStateOf<Set<String>>(emptySet())

    private val preferences by lazy {
        getSharedPreferences("shayan_brevent_engine", MODE_PRIVATE)
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST) {
                runOnUiThread {
                    refreshShizukuState()
                    operationMessage = if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        "مجوز Shizuku داده شد."
                    } else {
                        "مجوز Shizuku رد شد."
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        managedPackages = preferences.getStringSet("packages", emptySet())?.toSet() ?: emptySet()
        refreshInstalledApps()
        refreshShizukuState()
        refreshAccessibilityState()
        refreshOverlayPermissionState()

        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = if (dark) {
                darkColorScheme(
                    primary = Color(0xFFB7C8FF),
                    secondary = Color(0xFF9ED5C9),
                    background = Color(0xFF10141C),
                    surface = Color(0xFF191F2A),
                    surfaceVariant = Color(0xFF252D3A)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF365B9B),
                    secondary = Color(0xFF287D70),
                    background = Color(0xFFF5F7FC),
                    surface = Color.White,
                    surfaceVariant = Color(0xFFE8EDF6)
                )
            }

            MaterialTheme(colorScheme = scheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentPage) {
                        SuitePage.HOME -> HomeScreen(
                            deviceLabel = Build.MANUFACTURER + " " + Build.MODEL,
                            androidLabel = "Android " + Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT,
                            onOpenKillApps = { currentPage = SuitePage.KILL_APPS },
                            onOpenBrevent = { currentPage = SuitePage.BREVENT }
                        )
                        SuitePage.KILL_APPS -> {
                            BackHandler { currentPage = SuitePage.HOME }
                            EngineScreen(
                                title = "KillApps",
                                subtitle = "بستن برنامه‌های انتخاب‌شده",
                                isBrevent = false,
                                apps = installedApps,
                                shellRunning = shizukuRunning,
                                shellGranted = shizukuGranted,
                                operationMessage = operationMessage,
                                managedPackages = emptySet(),
                                onBack = { currentPage = SuitePage.HOME },
                                onConnect = ::connectShizuku,
                                accessibilityEnabled = accessibilityEnabled,
                                onEnableAccessibility = ::openAccessibilitySettings,
                                overlayPermissionGranted = overlayPermissionGranted,
                                onEnableOverlay = ::openOverlaySettings,
                                onStop = ::stopWithKillApps
                            )
                        }
                        SuitePage.BREVENT -> {
                            BackHandler { currentPage = SuitePage.HOME }
                            EngineScreen(
                                title = "Brevent",
                                subtitle = "مدیریت اجرای پس‌زمینه با قواعد AppOps",
                                isBrevent = true,
                                apps = installedApps,
                                shellRunning = shizukuRunning,
                                shellGranted = shizukuGranted,
                                operationMessage = operationMessage,
                                managedPackages = managedPackages,
                                onBack = { currentPage = SuitePage.HOME },
                                onConnect = ::connectShizuku,
                                onRestrict = ::restrictWithBrevent,
                                onRestore = ::restoreWithBrevent
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refreshShizukuState()
    }

    override fun onStop() {
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshShizukuState()
        refreshAccessibilityState()
        refreshOverlayPermissionState()
        refreshInstalledApps()
    }

    private fun refreshShizukuState() {
        shizukuRunning = ShizukuShell.isBinderAlive()
        shizukuGranted = ShizukuShell.hasPermission()
    }

    @Suppress("DEPRECATION")
    private fun refreshInstalledApps() {
        installedApps = try {
            val runningPackages = (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
                .runningAppProcesses.orEmpty()
                .flatMap { process -> process.pkgList?.toList().orEmpty() + listOfNotNull(process.processName) }
                .toSet()
            packageManager.getInstalledApplications(0)
                .asSequence()
                .filter { it.packageName != packageName }
                .mapNotNull { info ->
                    try {
                        InstalledApp(
                            label = packageManager.getApplicationLabel(info).toString(),
                            packageName = info.packageName,
                            icon = packageManager.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap(),
                            isSystem = (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0,
                            isRunning = info.packageName in runningPackages
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                .sortedBy { it.label.lowercase() }
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun connectShizuku() {
        refreshShizukuState()
        if (!shizukuRunning) {
            val launch = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            if (launch != null) {
                startActivity(launch)
            } else {
                openExternal("https://shizuku.rikka.app/")
            }
            operationMessage = "Shizuku را اجرا و سرویس آن را فعال کن؛ سپس به این برنامه برگرد."
            return
        }
        if (!shizukuGranted) {
            try {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST)
                operationMessage = "درخواست مجوز Shizuku ارسال شد."
            } catch (error: Exception) {
                operationMessage = "درخواست مجوز انجام نشد: " + (error.message ?: "خطای نامشخص")
            }
        } else {
            operationMessage = "اتصال Shizuku و مجوز آن آماده است."
        }
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            operationMessage = "مرورگر برای باز کردن لینک در دسترس نیست."
        }
    }

    private fun isKillAppsAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, KillAppsAccessibilityService::class.java).flattenToString()
        val enabled = Secure.getString(contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun refreshAccessibilityState() {
        accessibilityEnabled = isKillAppsAccessibilityEnabled()
    }

    private fun refreshOverlayPermissionState() {
        overlayPermissionGranted = Settings.canDrawOverlays(this)
    }

    private fun openOverlaySettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + packageName)
                )
            )
            operationMessage = "اجازهٔ نمایش روی سایر برنامه‌ها را فعال کن و به Shayan Suite برگرد."
        } catch (_: Exception) {
            operationMessage = "باز کردن تنظیمات نمایش روی سایر برنامه‌ها ممکن نشد."
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            operationMessage = "سرویس Shayan Suite را در بخش دسترس‌پذیری فعال کن و سپس به برنامه برگرد."
        } catch (_: Exception) {
            operationMessage = "باز کردن تنظیمات دسترس‌پذیری ممکن نشد."
        }
    }

    private fun stopWithKillApps(packages: Set<String>) {
        if (packages.isEmpty()) {
            operationMessage = "ابتدا حداقل یک برنامه را انتخاب کن."
            return
        }

        if (accessibilityEnabled || isKillAppsAccessibilityEnabled()) {
            if (!Settings.canDrawOverlays(this)) {
                openOverlaySettings()
                operationMessage = "برای نمایش درصد پیشرفت هنگام باز بودن تنظیمات، اجازهٔ نمایش روی سایر برنامه‌ها لازم است."
                return
            }
            val labels = installedApps.associate { it.packageName to it.label }
            val started = KillAppsAccessibilityService.start(this, packages.toList(), labels) { message ->
                operationMessage = message
            }
            if (started) {
                operationMessage = "عملیات KillApps آغاز شد."
                return
            }
            operationMessage = "سرویس دسترس‌پذیری هنوز متصل نشده است. یک‌بار آن را در تنظیمات خاموش و روشن کن."
            return
        }

        if (ShizukuShell.hasPermission()) {
            runPrivileged(
                KillAppsEngine.forceStopScript(packages),
                "درخواست توقف اجباری برای " + packages.size + " برنامه ارسال شد."
            )
        } else {
            val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            var requested = 0
            packages.forEach { packageName ->
                try {
                    manager.killBackgroundProcesses(packageName)
                    requested++
                } catch (_: Exception) {
                    // Android or the OEM may reject this request.
                }
            }
            operationMessage =
                "درخواست بستن پس‌زمینه برای " + requested + " برنامه فرستاده شد. این روش توقف اجباری کامل نیست."
        }
    }

    private fun restrictWithBrevent(packages: Set<String>) {
        if (packages.isEmpty()) {
            operationMessage = "ابتدا برنامه‌های موردنظر را انتخاب کن."
            return
        }
        if (!ShizukuShell.hasPermission()) {
            operationMessage = "برای اعمال قواعد Brevent، اتصال و مجوز Shizuku لازم است."
            return
        }
        runPrivileged(BreventEngine.restrictScript(packages)) {
            managedPackages = (managedPackages + packages).toSet()
            saveManagedPackages()
            operationMessage = "قواعد پس‌زمینه برای " + packages.size + " برنامه اعمال شد."
        }
    }

    private fun restoreWithBrevent(packages: Set<String>) {
        if (packages.isEmpty()) {
            operationMessage = "ابتدا برنامه‌هایی را که می‌خواهی از مدیریت خارج شوند انتخاب کن."
            return
        }
        if (!ShizukuShell.hasPermission()) {
            operationMessage = "برای بازگردانی قواعد، اتصال و مجوز Shizuku لازم است."
            return
        }
        runPrivileged(BreventEngine.restoreScript(packages)) {
            managedPackages = (managedPackages - packages).toSet()
            saveManagedPackages()
            operationMessage = "قواعد برنامه‌های انتخاب‌شده به حالت پیش‌فرض برگشت."
        }
    }

    private fun saveManagedPackages() {
        preferences.edit().putStringSet("packages", managedPackages.toSet()).apply()
    }

    private fun runPrivileged(script: String, successMessage: String) {
        runPrivileged(script) { operationMessage = successMessage }
    }

    private fun runPrivileged(script: String, onSuccess: () -> Unit) {
        if (!ShizukuShell.hasPermission()) {
            operationMessage = "Shizuku فعال نیست یا مجوز آن به Shayan Suite داده نشده است."
            return
        }
        operationMessage = "در حال اجرای عملیات..."
        ShizukuShell.execute(this, script) { result ->
            refreshShizukuState()
            if (result.exitCode == 0) {
                onSuccess()
            } else {
                operationMessage = "عملیات کامل نشد (کد " + result.exitCode + "): " +
                    (result.stderr.ifBlank { result.stdout }.ifBlank { "فرمان توسط سیستم رد شد." })
            }
        }
    }

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val SHIZUKU_PERMISSION_REQUEST = 100
    }
}

@Composable
private fun HomeScreen(
    deviceLabel: String,
    androidLabel: String,
    onOpenKillApps: () -> Unit,
    onOpenBrevent: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = "SHAYAN SUITE",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text("مرکز ابزارها", fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Text(
                "دو ابزار مستقل، داخل یک APK",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("محیط‌های داخلی مستقل", fontWeight = FontWeight.SemiBold)
                    Text(
                        "با انتخاب هر کارت، صفحهٔ همان ابزار در Shayan Suite باز می‌شود؛ APK جداگانه اجرا نمی‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SuiteAppCard(
            title = "Brevent",
            subtitle = "مدیریت اجرای برنامه‌ها در پس‌زمینه",
            details = "محیط مستقل مدیریت قواعد پس‌زمینه و بازگردانی آن‌ها.",
            icon = Icons.Rounded.Security,
            accent = Color(0xFF5578C7),
            onClick = onOpenBrevent
        )

        SuiteAppCard(
            title = "KillApps",
            subtitle = "بستن برنامه‌های در حال اجرا",
            details = "فهرست برنامه‌ها، انتخاب چندگانه و اجرای درخواست بستن.",
            icon = Icons.Rounded.Apps,
            accent = Color(0xFF258A79),
            onClick = onOpenKillApps
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("اطلاعات دستگاه", fontWeight = FontWeight.SemiBold)
                Text(deviceLabel, style = MaterialTheme.typography.bodyMedium)
                Text(
                    androidLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SuiteAppCard(
    title: String,
    subtitle: String,
    details: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(17.dp),
                    color = accent.copy(alpha = 0.13f),
                    modifier = Modifier.size(55.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(29.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF24936F))
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                Text("ورود به " + title)
            }
        }
    }
}

@Composable
private fun EngineScreen(
    title: String,
    subtitle: String,
    isBrevent: Boolean,
    apps: List<InstalledApp>,
    shellRunning: Boolean,
    shellGranted: Boolean,
    operationMessage: String,
    managedPackages: Set<String>,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onStop: ((Set<String>) -> Unit)? = null,
    onRestrict: ((Set<String>) -> Unit)? = null,
    onRestore: ((Set<String>) -> Unit)? = null,
    accessibilityEnabled: Boolean = false,
    onEnableAccessibility: (() -> Unit)? = null,
    overlayPermissionGranted: Boolean = true,
    onEnableOverlay: (() -> Unit)? = null
) {
    var selectedPackages by remember(title) { mutableStateOf(setOf<String>()) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("بازگشت")
            }
            Spacer(Modifier.weight(1f))
            Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }

        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!isBrevent && !accessibilityEnabled) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("دسترس‌پذیری KillApps", fontWeight = FontWeight.SemiBold)
                        Text(
                            "برای پردازش خودکار صفحهٔ توقف اجباری، این سرویس را فعال کن.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { onEnableAccessibility?.invoke() }) {
                        Text("فعال‌سازی")
                    }
                }
            }
        }

        if (!isBrevent && !overlayPermissionGranted) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("نمایش درصد پیشرفت", fontWeight = FontWeight.SemiBold)
                        Text(
                            "برای نمایش صفحهٔ پیشرفت روی تنظیمات اندروید، این اجازه لازم است.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { onEnableOverlay?.invoke() }) {
                        Text("فعال‌سازی")
                    }
                }
            }
        }

        if (isBrevent && !shellGranted) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (shellRunning) "Shizuku اجراست؛ مجوز برنامه لازم است" else "Shizuku متصل نیست",
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = onConnect) { Text("اتصال") }
                    }
                    Text(
                        "برای اعمال و بازگردانی قواعد پس‌زمینه، دسترسی ADB از طریق Shizuku لازم است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            if (isBrevent) {
                "در مدیریت Brevent: " + managedPackages.size + " برنامه در فهرست محلی ثبت شده"
            } else {
                "همه برنامه‌ها: " + apps.size + " · سیستمی: " + apps.count { it.isSystem } + " · انتخاب‌شده: " + selectedPackages.size
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        AppSelectionList(
            apps = apps,
            selectedPackages = selectedPackages,
            onToggle = { packageName ->
                selectedPackages = if (packageName in selectedPackages) selectedPackages - packageName
                else selectedPackages + packageName
            },
            modifier = Modifier.weight(1f)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { selectedPackages = apps.map { it.packageName }.toSet() }, modifier = Modifier.weight(1f)) {
                Text("انتخاب همه")
            }
            OutlinedButton(onClick = { selectedPackages = emptySet() }, modifier = Modifier.weight(1f)) {
                Text("پاک‌کردن انتخاب")
            }
        }

        if (isBrevent) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onRestrict?.invoke(selectedPackages) },
                    enabled = selectedPackages.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("اعمال محدودیت")
                }
                OutlinedButton(
                    onClick = { onRestore?.invoke(selectedPackages) },
                    enabled = selectedPackages.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("بازگردانی")
                }
            }
        } else {
            Button(
                onClick = { onStop?.invoke(selectedPackages) },
                enabled = selectedPackages.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("بستن " + selectedPackages.size + " برنامه")
            }
        }

        if (operationMessage.isNotBlank()) {
            Text(operationMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text(
            if (isBrevent) {
                "توجه: این هسته قواعد AppOps اندروید را مدیریت می‌کند و کل موتور اختصاصی Brevent اصلی نیست. رفتار قواعد ممکن است با نسخهٔ اندروید و سازنده فرق کند."
            } else {
                "برنامه‌های کاربری و سیستمی نمایش داده می‌شوند. توقف اجباری برخی برنامه‌های سیستمی ممکن است روی عملکرد گوشی اثر بگذارد."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun AppSelectionList(
    apps: List<InstalledApp>,
    selectedPackages: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var search by remember { mutableStateOf("") }
    val filtered = apps.filter {
        it.label.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("جست‌وجوی برنامه") }
        )

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("برنامه‌ای پیدا نشد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    Card(
                        shape = RoundedCornerShape(15.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().clickable { onToggle(app.packageName) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(38.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (app.isSystem) {
                                    Text("برنامهٔ سیستمی", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                                if (app.isRunning) {
                                    Text("در حال اجرا", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Checkbox(
                                checked = app.packageName in selectedPackages,
                                onCheckedChange = { onToggle(app.packageName) }
                            )
                        }
                    }
                }
            }
        }
    }
}
