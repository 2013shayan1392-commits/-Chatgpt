package com.shayan.apphub

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private enum class KillAppsListMode { ALL, CUSTOM }
private enum class KillAppsPermissionStep { DISCLOSURE, ACCESSIBILITY, OVERLAY }

@Composable
fun KillAppsScreen(
    apps: List<InstalledApp>,
    accessibilityEnabled: Boolean,
    overlayPermissionGranted: Boolean,
    operationMessage: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableOverlay: () -> Unit,
    onStop: (Set<String>, Boolean) -> Unit
) {
    val colors = darkColorScheme(
        primary = Color(0xFF0DB5AA),
        secondary = Color(0xFF0DB5AA),
        background = Color(0xFF515258),
        surface = Color(0xFF3B3C42),
        surfaceVariant = Color(0xFF303136),
        onSurface = Color(0xFFF3F3F6),
        onBackground = Color(0xFFF3F3F6),
        onPrimary = Color.White,
        outlineVariant = Color(0xFF666870)
    )
    MaterialTheme(colorScheme = colors) {
        KillAppsScreenContent(
            apps = apps,
            accessibilityEnabled = accessibilityEnabled,
            overlayPermissionGranted = overlayPermissionGranted,
            operationMessage = operationMessage,
            onBack = onBack,
            onRefresh = onRefresh,
            onEnableAccessibility = onEnableAccessibility,
            onEnableOverlay = onEnableOverlay,
            onStop = onStop
        )
    }
}

@Composable
private fun KillAppsScreenContent(
    apps: List<InstalledApp>,
    accessibilityEnabled: Boolean,
    overlayPermissionGranted: Boolean,
    operationMessage: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableOverlay: () -> Unit,
    onStop: (Set<String>, Boolean) -> Unit
) {
    val context = LocalContext.current
    val killPrefs = remember(context) { context.getSharedPreferences("killapps_ui_preferences", Context.MODE_PRIVATE) }
    var mode by remember { mutableStateOf(if (killPrefs.getString("list_mode", "CUSTOM") == "ALL") KillAppsListMode.ALL else KillAppsListMode.CUSTOM) }
    var includeUserApps by remember { mutableStateOf(killPrefs.getBoolean("include_user_apps", true)) }
    var includeSystemApps by remember { mutableStateOf(killPrefs.getBoolean("include_system_apps", true)) }
    var showInstalledApps by remember { mutableStateOf(killPrefs.getBoolean("show_installed_apps", false)) }
    var selectedPackages by remember {
        mutableStateOf(killPrefs.getStringSet("selected_packages", emptySet())?.toSet() ?: emptySet())
    }
    var searchVisible by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var showGuide by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showExceptionsDialog by remember { mutableStateOf(false) }
    var showFailureDialog by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var permissionStep by remember { mutableStateOf<KillAppsPermissionStep?>(null) }
    var showProgressWindow by remember { mutableStateOf(killPrefs.getBoolean("show_progress_window", true)) }
    var exceptionPackages by remember {
        mutableStateOf(killPrefs.getStringSet("exception_packages", emptySet())?.toSet() ?: emptySet())
    }
    var showFailureList by remember { mutableStateOf(killPrefs.getBoolean("show_failure_list", true)) }
    var failedPackages by remember {
        mutableStateOf(killPrefs.getStringSet("failed_packages", emptySet())?.toSet() ?: emptySet())
    }
    var memory by remember { mutableStateOf(readMemory(context)) }

    LaunchedEffect(operationMessage) {
        failedPackages = killPrefs.getStringSet("failed_packages", emptySet())?.toSet() ?: emptySet()
    }

    val runningCount = apps.count { it.isRunning }
    val visibleApps = remember(apps, includeUserApps, includeSystemApps, searchText, showInstalledApps) {
        apps.filter { app ->
            val categoryEnabled = if (app.isSystem) includeSystemApps else includeUserApps
            categoryEnabled && (showInstalledApps || app.isRunning) && (
                searchText.isBlank() ||
                    app.label.contains(searchText, ignoreCase = true) ||
                    app.packageName.contains(searchText, ignoreCase = true)
                )
        }
    }
    // Browsing may include installed apps, but the stop queue contains only running processes.
    val eligibleApps = visibleApps.filter { it.isRunning && it.packageName !in exceptionPackages }
    val eligiblePackages = eligibleApps.map { it.packageName }.toSet()
    val selectedVisible = selectedPackages.intersect(eligiblePackages)
    val packagesToClose = if (mode == KillAppsListMode.ALL) eligiblePackages else selectedVisible

    fun requestClose() {
        when {
            packagesToClose.isEmpty() -> Unit
            !accessibilityEnabled -> permissionStep = KillAppsPermissionStep.DISCLOSURE
            showProgressWindow && !overlayPermissionGranted -> permissionStep = KillAppsPermissionStep.OVERLAY
            else -> onStop(packagesToClose, showProgressWindow)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "بازگشت")
            }
            Text(
                "KillApps Pro",
                modifier = Modifier.weight(1f),
                fontSize = 21.sp,
                fontWeight = FontWeight.Normal
            )
            IconButton(onClick = { showGuide = true }) {
                Icon(Icons.Rounded.HelpOutline, contentDescription = "راهنما")
            }
            Box {
                IconButton(onClick = { overflowExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "بیشتر")
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("تنظیمات") },
                        leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        onClick = { overflowExpanded = false; showSettings = true }
                    )
                    DropdownMenuItem(
                        text = { Text("فهرست استثناها") },
                        leadingIcon = { Icon(Icons.Rounded.Bookmark, contentDescription = null) },
                        onClick = { overflowExpanded = false; showExceptionsDialog = true }
                    )
                    if (showFailureList) {
                        DropdownMenuItem(
                            text = { Text("موارد ناموفق") },
                            leadingIcon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null) },
                            onClick = { overflowExpanded = false; showFailureDialog = true }
                        )
                    }
                }
            }
        }
        }
        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                MemoryCard(
                    runningCount = runningCount,
                    usedBytes = (memory.first - memory.second).coerceAtLeast(0L),
                    totalBytes = memory.first,
                    freeBytes = memory.second,
                    onRefresh = {
                        memory = readMemory(context)
                        onRefresh()
                    }
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(15.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        CategorySwitchRow(
                            label = "برنامه‌های کاربری",
                            checked = includeUserApps,
                            onCheckedChange = {
                                includeUserApps = it
                                killPrefs.edit().putBoolean("include_user_apps", it).apply()
                            }
                        )
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        CategorySwitchRow(
                            label = "برنامه‌های سیستمی",
                            checked = includeSystemApps,
                            onCheckedChange = {
                                includeSystemApps = it
                                killPrefs.edit().putBoolean("include_system_apps", it).apply()
                            }
                        )
                        Text(
                            "توقف اجباری بعضی برنامه‌های سیستمی ممکن است روی عملکرد گوشی اثر بگذارد.",
                            modifier = Modifier.padding(top = 4.dp, bottom = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeChoice(
                        text = "برنامه‌های درحال اجرا",
                        selected = !showInstalledApps,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showInstalledApps = false
                            killPrefs.edit().putBoolean("show_installed_apps", false).apply()
                        }
                    )
                    ModeChoice(
                        text = "همهٔ برنامه‌های نصب‌شده",
                        selected = showInstalledApps,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showInstalledApps = true
                            killPrefs.edit().putBoolean("show_installed_apps", true).apply()
                        }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeChoice(
                        text = "همهٔ برنامه‌ها",
                        selected = mode == KillAppsListMode.ALL,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            mode = KillAppsListMode.ALL
                            killPrefs.edit().putString("list_mode", mode.name).apply()
                        }
                    )
                    ModeChoice(
                        text = "فهرست سفارشی",
                        selected = mode == KillAppsListMode.CUSTOM,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            mode = KillAppsListMode.CUSTOM
                            killPrefs.edit().putString("list_mode", mode.name).apply()
                        }
                    )
                }
            }

            if (searchVisible) {
                item {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("جست‌وجوی برنامه") }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("برنامه‌ها", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            when (mode) {
                                KillAppsListMode.ALL -> "${eligibleApps.size} برنامهٔ درحال اجرا برای پردازش · ${exceptionPackages.size} استثنا"
                                KillAppsListMode.CUSTOM -> "${selectedVisible.size} انتخاب‌شده از ${eligibleApps.size} برنامهٔ درحال اجرا · ${exceptionPackages.size} استثنا"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) searchText = "" }) {
                        Icon(Icons.Rounded.Search, contentDescription = "جست‌وجو")
                    }
                    IconButton(onClick = { memory = readMemory(context); onRefresh() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "به‌روزرسانی")
                    }
                }
            }

            if (operationMessage.isNotBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            operationMessage,
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (visibleApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("برنامه‌ای برای نمایش پیدا نشد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(visibleApps, key = { it.packageName }) { app ->
                    KillAppsAppRow(
                        app = app,
                        selectable = mode == KillAppsListMode.CUSTOM && app.packageName !in exceptionPackages,
                        selected = app.packageName in selectedVisible,
                        isException = app.packageName in exceptionPackages,
                        onClick = {
                            if (mode == KillAppsListMode.CUSTOM && app.packageName !in exceptionPackages) {
                                selectedPackages = if (app.packageName in selectedPackages) {
                                    selectedPackages - app.packageName
                                } else {
                                    selectedPackages + app.packageName
                                }
                                killPrefs.edit().putStringSet("selected_packages", selectedPackages.toSet()).apply()
                            }
                        }
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 5.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (mode == KillAppsListMode.ALL) "${packagesToClose.size} برنامه برای پردازش" else "${selectedVisible.size} برنامه انتخاب شده",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (mode == KillAppsListMode.ALL) "حالت همهٔ برنامه‌ها" else "حالت فهرست سفارشی",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { requestClose() },
                    enabled = packagesToClose.isNotEmpty(),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 13.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("شروع")
                }
            }
        }
    }

    when (permissionStep) {
        KillAppsPermissionStep.DISCLOSURE -> AlertDialog(
            onDismissRequest = { permissionStep = null },
            title = { Text("استفاده از سرویس دسترس‌پذیری") },
            text = {
                Text(
                    "برای پیدا کردن دکمهٔ «توقف اجباری» در تنظیمات سیستم، سرویس باید محتوای پنجرهٔ فعال و رویدادهای تغییر صفحه را بررسی کند و روی دکمهٔ مربوط بزند. این سرویس فقط برای همین فرایند در خود دستگاه استفاده می‌شود؛ هیچ داده‌ای از طریق سرویس دسترس‌پذیری جمع‌آوری یا ارسال نمی‌شود."
                )
            },
            confirmButton = {
                TextButton(onClick = { permissionStep = KillAppsPermissionStep.ACCESSIBILITY }) {
                    Text("موافقت و ادامه")
                }
            },
            dismissButton = {
                TextButton(onClick = { permissionStep = null }) { Text("لغو") }
            }
        )
        KillAppsPermissionStep.ACCESSIBILITY -> AlertDialog(
            onDismissRequest = { permissionStep = null },
            title = { Text("مجوز دسترس‌پذیری") },
            text = {
                Text("KillApps برای بستن برنامه‌های دیگر به مجوز سرویس دسترس‌پذیری نیاز دارد.")
            },
            confirmButton = {
                TextButton(onClick = {
                    permissionStep = null
                    onEnableAccessibility()
                }) { Text("نمایش تنظیمات") }
            },
            dismissButton = {
                TextButton(onClick = { permissionStep = null }) { Text("لغو") }
            }
        )
        KillAppsPermissionStep.OVERLAY -> AlertDialog(
            onDismissRequest = { permissionStep = null },
            title = { Text("مجوز نمایش روی برنامه‌های دیگر") },
            text = {
                Text("KillApps برای نمایش صفحهٔ انتظار هنگام بستن برنامه‌ها، به اجازهٔ نمایش روی سایر برنامه‌ها نیاز دارد.")
            },
            confirmButton = {
                TextButton(onClick = {
                    permissionStep = null
                    onEnableOverlay()
                }) { Text("نمایش تنظیمات") }
            },
            dismissButton = {
                TextButton(onClick = { permissionStep = null }) { Text("لغو") }
            }
        )
        null -> Unit
    }

    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            title = { Text("راهنمای KillApps") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("۱. گروه برنامه‌های کاربری یا سیستمی را فعال کن.")
                    Text("۲. «همهٔ برنامه‌ها» یا «فهرست سفارشی» را انتخاب کن.")
                    Text("۳. در فهرست سفارشی، برنامه‌های موردنظر را علامت بزن.")
                    Text("۴. دکمهٔ شروع را بزن و مجوزهای لازم را فعال کن.")
                    Text("برنامه‌هایی که سیستم اجازهٔ توقفشان را ندهد ممکن است بسته نشوند یا دوباره اجرا شوند.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showGuide = false }) { Text("بستن") }
            }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("تنظیمات KillApps") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("نمایش پنجرهٔ پیشرفت", fontWeight = FontWeight.Medium)
                            Text(
                                "هنگام بستن برنامه‌ها، درصد پیشرفت نمایش داده شود.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showProgressWindow,
                            onCheckedChange = {
                                showProgressWindow = it
                                killPrefs.edit().putBoolean("show_progress_window", it).apply()
                            }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("نمایش فهرست بستن ناموفق", fontWeight = FontWeight.Medium)
                            Text(
                                "مواردی که دکمهٔ توقف آن‌ها پیدا نشود یا کلیک رد شود ثبت می‌شوند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showFailureList,
                            onCheckedChange = {
                                showFailureList = it
                                killPrefs.edit().putBoolean("show_failure_list", it).apply()
                            }
                        )
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(onClick = {
                        showSettings = false
                        showExceptionsDialog = true
                    }) {
                        Text("مدیریت فهرست استثناها (${exceptionPackages.size})")
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("مجوز دسترس‌پذیری: ${if (accessibilityEnabled) "فعال" else "غیرفعال"}")
                    if (showProgressWindow) {
                        Text("نمایش روی برنامه‌های دیگر: ${if (overlayPermissionGranted) "فعال" else "غیرفعال"}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("بستن") }
            }
        )
    }

    if (showExceptionsDialog) {
        AlertDialog(
            onDismissRequest = { showExceptionsDialog = false },
            title = { Text("فهرست استثناها") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "برنامه‌های این فهرست در هیچ‌یک از حالت‌های بستن انتخاب نمی‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    apps.forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = app.icon,
                                contentDescription = app.label,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                app.label,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Checkbox(
                                checked = app.packageName in exceptionPackages,
                                onCheckedChange = { checked ->
                                    exceptionPackages = if (checked) exceptionPackages + app.packageName
                                    else exceptionPackages - app.packageName
                                    killPrefs.edit().putStringSet("exception_packages", exceptionPackages.toSet()).apply()
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExceptionsDialog = false }) { Text("تمام") }
            }
        )
    }


    if (showFailureDialog) {
        val failedApps = apps.filter { it.packageName in failedPackages }
        AlertDialog(
            onDismissRequest = { showFailureDialog = false },
            title = { Text("فهرست بستن ناموفق") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "${failedPackages.size} مورد از آخرین عملیات ثبت شده است. فقط خطاهای قابل‌شناسایی در این فهرست می‌آیند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (failedApps.isEmpty()) {
                        Text("مورد ناموفقی ثبت نشده است.")
                    } else {
                        failedApps.forEach { app ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.foundation.Image(
                                    bitmap = app.icon,
                                    contentDescription = app.label,
                                    modifier = Modifier.size(30.dp)
                                )
                                Text(app.label, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        failedPackages = emptySet()
                        killPrefs.edit().putStringSet("failed_packages", emptySet()).apply()
                        showFailureDialog = false
                    }) { Text("پاک‌کردن فهرست") }
                    TextButton(onClick = { showFailureDialog = false }) { Text("بستن") }
                }
            }
        )
    }

    }

@Composable
private fun MemoryCard(
    runningCount: Int,
    usedBytes: Long,
    totalBytes: Long,
    freeBytes: Long,
    onRefresh: () -> Unit
) {
    val trackColor = Color(0xFFE0E2EE)
    val progressColor = MaterialTheme.colorScheme.primary
    val ratio = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(7.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 166.dp).padding(horizontal = 9.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Column(
                modifier = Modifier.weight(1.05f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(126.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 3.5.dp.toPx()
                        drawArc(
                            color = trackColor,
                            startAngle = 135f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = progressColor,
                            startAngle = 135f,
                            sweepAngle = ratio * 360f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text((ratio * 100).toInt().toString(), fontSize = 31.sp, fontWeight = FontWeight.Light, color = progressColor)
                            Text("%", fontSize = 15.sp, color = progressColor, modifier = Modifier.padding(top = 5.dp))
                        }
                        Text("RAM", fontSize = 11.sp, color = progressColor)
                    }
                }
                Text(
                    formatMemory(usedBytes) + " / " + formatMemory(totalBytes),
                    fontSize = 10.sp,
                    color = progressColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
            Column(
                modifier = Modifier.weight(1.12f).padding(start = 4.dp, end = 3.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(runningCount.toString(), color = progressColor, fontSize = 27.sp, fontWeight = FontWeight.Light)
                    Text("برنامه‌های درحال اجرا", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
                }
                Spacer(Modifier.height(17.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatMemory(freeBytes), color = progressColor, fontSize = 21.sp)
                    Text("باقیمانده RAM", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            IconButton(onClick = onRefresh, modifier = Modifier.align(Alignment.Top).size(30.dp)) {
                Icon(Icons.Rounded.Refresh, contentDescription = "به‌روزرسانی RAM", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
    }
}

@Composable
private fun CategorySwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    }
}

@Composable
private fun ModeChoice(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun KillAppsAppRow(
    app: InstalledApp,
    selectable: Boolean,
    selected: Boolean,
    isException: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            androidx.compose.foundation.Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.size(38.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(app.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (isException) "فهرست استثناها" else if (app.isSystem) "برنامهٔ سیستمی" else if (app.isRunning) "در حال اجرا" else "برنامهٔ کاربری",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (selectable) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
        }
    }
}

private fun readMemory(context: Context): Pair<Long, Long> {
    return try {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        info.totalMem to info.availMem
    } catch (_: Exception) {
        0L to 0L
    }
}

private fun formatMemory(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) String.format(Locale.US, "%.2f GB", gb)
    else String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
}
