package com.shayan.apphub.killapps

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.provider.Settings.Secure
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.util.Locale

private const val CLEAN_PREFS = "killapps_fresh_state_v1"
private const val RECENT_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L

data class KillAppsPackage(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isPersistent: Boolean,
    val lastUsedAt: Long,
    val isProtected: Boolean
)

private enum class KillAppsFilter {
    RECENT, ALL, PERSISTENT, CUSTOM, EXCEPTIONS, FAILED
}

class CleanKillAppsActivity : ComponentActivity() {
    private val resumeVersion = mutableStateOf(0)
    private val repository by lazy { KillAppsRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CleanKillAppsScreen(
                    repository = repository,
                    resumeVersion = resumeVersion.value,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeVersion.value += 1
    }
}

private class KillAppsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(CLEAN_PREFS, Context.MODE_PRIVATE)

    fun loadApps(): List<KillAppsPackage> {
        val now = System.currentTimeMillis()
        val lastUsed = readLastUsed()
        return try {
            appContext.packageManager.getInstalledApplications(0)
                .asSequence()
                .filter { it.packageName != appContext.packageName }
                .map { info ->
                    val label = try {
                        appContext.packageManager.getApplicationLabel(info).toString()
                    } catch (_: Exception) {
                        info.packageName
                    }
                    val system = (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                    val persistent = (info.flags and ApplicationInfo.FLAG_PERSISTENT) != 0
                    KillAppsPackage(
                        packageName = info.packageName,
                        label = label,
                        isSystem = system,
                        isPersistent = persistent,
                        lastUsedAt = lastUsed[info.packageName] ?: 0L,
                        isProtected = isCorePackage(info.packageName)
                    )
                }
                .sortedWith(
                    compareByDescending<KillAppsPackage> { it.lastUsedAt }
                        .thenBy { it.label.lowercase(Locale.ROOT) }
                )
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readLastUsed(): Map<String, Long> {
        return try {
            val manager = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - RECENT_WINDOW_MS
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                .orEmpty()
                .groupBy { it.packageName }
                .mapValues { entry -> entry.value.maxOfOrNull { it.lastTimeUsed } ?: 0L }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun hasUsageAccess(): Boolean {
        return try {
            val ops = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName)
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun isAccessibilityEnabled(): Boolean {
        val expected = android.content.ComponentName(
            appContext,
            CleanKillAppsAccessibilityService::class.java
        ).flattenToString()
        val enabled = Secure.getString(
            appContext.contentResolver,
            Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun exceptions(): Set<String> =
        prefs.getStringSet("exceptions", emptySet())?.toSet() ?: emptySet()

    fun customSelection(): Set<String> =
        prefs.getStringSet("custom_selection", emptySet())?.toSet() ?: emptySet()

    fun setCustomSelection(values: Set<String>) {
        prefs.edit().putStringSet("custom_selection", values.toSet()).apply()
    }

    fun failed(): Set<String> =
        prefs.getStringSet("failed", emptySet())?.toSet() ?: emptySet()

    fun setExceptions(values: Set<String>) {
        prefs.edit().putStringSet("exceptions", values.toSet()).apply()
    }

    fun setFailed(values: Set<String>) {
        prefs.edit().putStringSet("failed", values.toSet()).apply()
    }

    fun showProgress(): Boolean = prefs.getBoolean("show_progress", true)

    fun setShowProgress(value: Boolean) {
        prefs.edit().putBoolean("show_progress", value).apply()
    }

    private fun isCorePackage(packageName: String): Boolean {
        val protected = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.providers.settings",
            "com.google.android.permissioncontroller",
            "com.huawei.android.launcher",
            "com.miui.home",
            "com.sec.android.app.launcher"
        )
        return packageName in protected
    }
}

@Composable
private fun CleanKillAppsScreen(
    repository: KillAppsRepository,
    resumeVersion: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf(repository.loadApps()) }
    var filter by remember { mutableStateOf(KillAppsFilter.RECENT) }
    var search by remember { mutableStateOf("") }
    var includeUser by remember { mutableStateOf(true) }
    var includeSystem by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var customSelection by remember { mutableStateOf(repository.customSelection()) }
    var exceptions by remember { mutableStateOf(repository.exceptions()) }
    var failed by remember { mutableStateOf(repository.failed()) }
    var usageAccess by remember { mutableStateOf(repository.hasUsageAccess()) }
    var accessibilityEnabled by remember { mutableStateOf(repository.isAccessibilityEnabled()) }
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var showProgress by remember { mutableStateOf(repository.showProgress()) }
    var showSettings by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }

    LaunchedEffect(resumeVersion) {
        apps = repository.loadApps()
        exceptions = repository.exceptions()
        failed = repository.failed()
        customSelection = repository.customSelection()
        usageAccess = repository.hasUsageAccess()
        accessibilityEnabled = repository.isAccessibilityEnabled()
        overlayAllowed = Settings.canDrawOverlays(context)
        showProgress = repository.showProgress()
    }

    val now = System.currentTimeMillis()
    val visibleApps = apps.filter { app ->
        val categoryAllowed = if (app.isSystem) includeSystem else includeUser
        val filterAllowed = when (filter) {
            KillAppsFilter.RECENT -> app.lastUsedAt >= now - RECENT_WINDOW_MS
            KillAppsFilter.ALL -> true
            KillAppsFilter.PERSISTENT -> app.isPersistent
            KillAppsFilter.CUSTOM -> app.packageName in customSelection
            KillAppsFilter.EXCEPTIONS -> app.packageName in exceptions
            KillAppsFilter.FAILED -> app.packageName in failed
        }
        categoryAllowed && filterAllowed && (
            search.isBlank() ||
                app.label.contains(search, ignoreCase = true) ||
                app.packageName.contains(search, ignoreCase = true)
            )
    }

    fun openAccessibilitySettings() {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        notice = "سرویس دسترس‌پذیری Shayan Suite را فعال کن و به همین صفحه برگرد."
    }

    fun openUsageSettings() {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        notice = "اجازهٔ دسترسی به آمار استفاده را فعال کن تا فهرست برنامه‌های اخیراً استفاده‌شده دقیق‌تر شود."
    }

    fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        notice = "اجازهٔ نمایش روی سایر برنامه‌ها را فعال کن و دوباره به KillApps برگرد."
    }

    fun addOrRemoveExceptions() {
        if (selected.isEmpty()) {
            notice = "ابتدا برنامه‌ها را انتخاب کن."
            return
        }
        exceptions = if (filter == KillAppsFilter.EXCEPTIONS) {
            exceptions - selected
        } else {
            exceptions + selected
        }
        repository.setExceptions(exceptions)
        selected = emptySet()
        notice = "فهرست استثناها ذخیره شد."
    }

    fun startClosing() {
        if (selected.isEmpty()) {
            notice = "برای شروع، دست‌کم یک برنامه را انتخاب کن."
            return
        }
        if (!accessibilityEnabled) {
            openAccessibilitySettings()
            return
        }
        val queue = selected.filter { it !in exceptions }
        if (queue.isEmpty()) {
            notice = "تمام برنامه‌های انتخاب‌شده در فهرست استثناها هستند."
            return
        }
        val labels = apps.associate { it.packageName to it.label }
        val useOverlay = showProgress && overlayAllowed
        if (showProgress && !overlayAllowed) {
            notice = "پنجرهٔ پیشرفت فعال نیست؛ عملیات بدون پنجرهٔ شناور شروع می‌شود. برای فعال‌کردن آن از تنظیمات مجوز نمایش را بده."
        } else {
            notice = "درخواست بستن برنامه‌ها ثبت شد."
        }
        val started = CleanKillAppsAccessibilityService.enqueue(
            context = context,
            packages = queue,
            labels = labels,
            showProgress = useOverlay,
            onFinished = { failures ->
                failed = failures
                repository.setFailed(failures)
            }
        )
        if (!started) {
            notice = "سرویس فعال نیست یا عملیات دیگری در جریان است. وضعیت دسترس‌پذیری را بررسی کن."
        } else {
            selected = emptySet()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "بازگشت")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("KillApps", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(
                    "مدیریت و بستن برنامه‌های انتخاب‌شده",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                apps = repository.loadApps()
                notice = "فهرست برنامه‌ها به‌روزرسانی شد."
            }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "به‌روزرسانی")
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Rounded.Settings, contentDescription = "تنظیمات")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(13.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Metric(value = apps.size.toString(), label = "برنامه‌ها")
                Metric(value = visibleApps.size.toString(), label = "نمایش")
                Metric(value = selected.size.toString(), label = "انتخاب")
                Metric(value = exceptions.size.toString(), label = "استثنا")
            }
        }

        if (!accessibilityEnabled) {
            PermissionCard(
                title = "دسترسی دسترس‌پذیری لازم است",
                description = "این دسترسی برای بازکردن صفحهٔ اطلاعات برنامه و انجام توقف اجباری لازم است.",
                action = "فعال‌سازی",
                onClick = ::openAccessibilitySettings
            )
        }
        if (!usageAccess) {
            PermissionCard(
                title = "آمار استفاده اختیاری است",
                description = "بدون این اجازه، تشخیص برنامه‌های اخیراً استفاده‌شده ممکن است ناقص باشد.",
                action = "تنظیم",
                onClick = ::openUsageSettings
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            FilterChip(
                selected = filter == KillAppsFilter.RECENT,
                onClick = { filter = KillAppsFilter.RECENT; selected = emptySet() },
                label = { Text("اخیر") }
            )
            FilterChip(
                selected = filter == KillAppsFilter.ALL,
                onClick = { filter = KillAppsFilter.ALL; selected = emptySet() },
                label = { Text("همه") }
            )
            FilterChip(
                selected = filter == KillAppsFilter.PERSISTENT,
                onClick = { filter = KillAppsFilter.PERSISTENT; selected = emptySet() },
                label = { Text("ماندگار") }
            )
            FilterChip(
                selected = filter == KillAppsFilter.CUSTOM,
                onClick = { filter = KillAppsFilter.CUSTOM; selected = emptySet() },
                label = { Text("سفارشی") }
            )
            FilterChip(
                selected = filter == KillAppsFilter.EXCEPTIONS,
                onClick = { filter = KillAppsFilter.EXCEPTIONS; selected = emptySet() },
                label = { Text("استثناها") }
            )
            FilterChip(
                selected = filter == KillAppsFilter.FAILED,
                onClick = { filter = KillAppsFilter.FAILED; selected = emptySet() },
                label = { Text("ناموفق‌ها") }
            )
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("جست‌وجوی برنامه یا نام بسته") }
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeUser, onCheckedChange = { includeUser = it })
                    Text("کاربری", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeSystem, onCheckedChange = { includeSystem = it })
                    Text("سیستمی", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = {
                    selected = if (filter == KillAppsFilter.EXCEPTIONS || filter == KillAppsFilter.FAILED) {
                        visibleApps.map { it.packageName }.toSet()
                    } else {
                        visibleApps.filterNot { it.isProtected || it.packageName in exceptions }
                            .map { it.packageName }.toSet()
                    }
                }) {
                    Text("انتخاب فهرست")
                }
            }
        }

        if (visibleApps.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (filter) {
                        KillAppsFilter.RECENT -> if (usageAccess) "برنامه‌ای در بازهٔ اخیر پیدا نشد." else "برای مشاهدهٔ فهرست اخیر، آمار استفاده را فعال کن."
                        KillAppsFilter.ALL -> "برنامه‌ای پیدا نشد."
                        KillAppsFilter.PERSISTENT -> "برنامهٔ ماندگاری پیدا نشد."
                        KillAppsFilter.CUSTOM -> "فهرست سفارشی خالی است."
                        KillAppsFilter.EXCEPTIONS -> "فهرست استثناها خالی است."
                        KillAppsFilter.FAILED -> "مورد ناموفقی ثبت نشده است."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(visibleApps, key = { it.packageName }) { item ->
                    KillAppsRow(
                        item = item,
                        context = context,
                        checked = item.packageName in selected,
                        isException = item.packageName in exceptions,
                        failed = item.packageName in failed,
                        inExceptionView = filter == KillAppsFilter.EXCEPTIONS,
                        onChecked = { checked ->
                            selected = if (checked) selected + item.packageName else selected - item.packageName
                        }
                    )
                }
            }
        }

        if (notice.isNotBlank()) {
            Text(
                notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (filter == KillAppsFilter.CUSTOM) {
            OutlinedButton(
                onClick = {
                    customSelection = customSelection - selected
                    repository.setCustomSelection(customSelection)
                    selected = emptySet()
                    notice = "برنامه‌های انتخاب‌شده از فهرست سفارشی حذف شدند."
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("حذف از فهرست سفارشی") }
        } else if (filter == KillAppsFilter.EXCEPTIONS) {
            OutlinedButton(
                onClick = ::addOrRemoveExceptions,
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("حذف از فهرست استثناها")
            }
        } else if (filter != KillAppsFilter.FAILED) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (selected.isEmpty()) {
                            notice = "ابتدا برنامه‌ها را انتخاب کن."
                        } else {
                            customSelection = customSelection + selected
                            repository.setCustomSelection(customSelection)
                            selected = emptySet()
                            notice = "فهرست سفارشی ذخیره شد."
                        }
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("فهرست سفارشی")
                }
                OutlinedButton(
                    onClick = ::addOrRemoveExceptions,
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Bookmark, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("استثنا")
                }
                Button(
                    onClick = ::startClosing,
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("شروع بستن")
                }
            }
        } else {
            OutlinedButton(
                onClick = {
                    failed = emptySet()
                    repository.setFailed(emptySet())
                    notice = "فهرست خطاها پاک شد."
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("پاک‌کردن فهرست خطاها")
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("تنظیمات KillApps") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("پنجرهٔ پیشرفت شناور", modifier = Modifier.weight(1f))
                        Switch(
                            checked = showProgress,
                            onCheckedChange = {
                                showProgress = it
                                repository.setShowProgress(it)
                            }
                        )
                    }
                    Text(
                        "پنجرهٔ شناور به اجازهٔ نمایش روی برنامه‌های دیگر نیاز دارد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        showSettings = false
                        openOverlaySettings()
                    }) { Text("مجوز پنجرهٔ شناور") }
                    TextButton(onClick = {
                        showSettings = false
                        openAccessibilitySettings()
                    }) { Text("تنظیم دسترس‌پذیری") }
                    TextButton(onClick = {
                        showSettings = false
                        openUsageSettings()
                    }) { Text("مجوز آمار استفاده") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("بستن") }
            }
        )
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    action: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun KillAppsRow(
    item: KillAppsPackage,
    context: Context,
    checked: Boolean,
    isException: Boolean,
    failed: Boolean,
    inExceptionView: Boolean,
    onChecked: (Boolean) -> Unit
) {
    val icon = remember(item.packageName) {
        try {
            context.packageManager.getApplicationIcon(item.packageName)
                .toBitmap(88, 88)
                .asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !item.isProtected || inExceptionView) {
                onChecked(!checked)
            },
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = item.label, modifier = Modifier.size(39.dp))
            } else {
                Surface(modifier = Modifier.size(39.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.isSystem) SmallTag("سیستمی")
                    if (item.isPersistent) SmallTag("ماندگار")
                    if (isException) SmallTag("استثنا")
                    if (failed) SmallTag("ناموفق")
                    if (item.isProtected) SmallTag("محافظت‌شده")
                }
            }
            Checkbox(
                checked = checked,
                enabled = !item.isProtected || inExceptionView,
                onCheckedChange = onChecked
            )
        }
    }
}

@Composable
private fun SmallTag(text: String) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
