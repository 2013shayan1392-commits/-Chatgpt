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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawArc
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    onStop: (Set<String>) -> Unit
) {
    var mode by remember { mutableStateOf(KillAppsListMode.CUSTOM) }
    var includeUserApps by remember { mutableStateOf(true) }
    var includeSystemApps by remember { mutableStateOf(true) }
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var showGuide by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var permissionStep by remember { mutableStateOf<KillAppsPermissionStep?>(null) }
    val context = LocalContext.current
    var memory by remember { mutableStateOf(readMemory(context)) }

    val visibleApps = remember(apps, includeUserApps, includeSystemApps, searchText) {
        apps.filter { app ->
            val categoryEnabled = if (app.isSystem) includeSystemApps else includeUserApps
            categoryEnabled && (
                searchText.isBlank() ||
                    app.label.contains(searchText, ignoreCase = true) ||
                    app.packageName.contains(searchText, ignoreCase = true)
                )
        }
    }
    val selectedVisible = selectedPackages.intersect(visibleApps.map { it.packageName }.toSet())
    val packagesToClose = if (mode == KillAppsListMode.ALL) {
        visibleApps.map { it.packageName }.toSet()
    } else {
        selectedVisible
    }

    fun requestClose() {
        when {
            packagesToClose.isEmpty() -> Unit
            !accessibilityEnabled -> permissionStep = KillAppsPermissionStep.DISCLOSURE
            !overlayPermissionGranted -> permissionStep = KillAppsPermissionStep.OVERLAY
            else -> onStop(packagesToClose)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "بازگشت")
            }
            Text(
                "KillApps",
                modifier = Modifier.weight(1f),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { showGuide = true }) {
                Icon(Icons.Rounded.Info, contentDescription = "راهنما")
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Rounded.Settings, contentDescription = "تنظیمات")
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
                            onCheckedChange = { includeUserApps = it }
                        )
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        CategorySwitchRow(
                            label = "برنامه‌های سیستمی",
                            checked = includeSystemApps,
                            onCheckedChange = { includeSystemApps = it }
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
                        text = "همهٔ برنامه‌ها",
                        selected = mode == KillAppsListMode.ALL,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = KillAppsListMode.ALL }
                    )
                    ModeChoice(
                        text = "فهرست سفارشی",
                        selected = mode == KillAppsListMode.CUSTOM,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = KillAppsListMode.CUSTOM }
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
                                KillAppsListMode.ALL -> "${visibleApps.size} برنامه در گروه‌های فعال"
                                KillAppsListMode.CUSTOM -> "${selectedVisible.size} برنامه انتخاب‌شده از ${visibleApps.size}"
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
                        selectable = mode == KillAppsListMode.CUSTOM,
                        selected = app.packageName in selectedVisible,
                        onClick = {
                            if (mode == KillAppsListMode.CUSTOM) {
                                selectedPackages = if (app.packageName in selectedPackages) {
                                    selectedPackages - app.packageName
                                } else {
                                    selectedPackages + app.packageName
                                }
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

    if (operationMessage.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("KillApps") },
            text = { Text(operationMessage) },
            confirmButton = {
                TextButton(onClick = { }) {
                    Text("باشه")
                }
            }
        )
    }

    when (permissionStep) {
        KillAppsPermissionStep.DISCLOSURE -> AlertDialog(
            onDismissRequest = { permissionStep = null },
            title = { Text("استفاده از سرویس دسترس‌پذیری") },
            text = {
                Text(
                    "برای پیدا کردن دکمهٔ «توقف اجباری» در تنظیمات سیستم، سرویس باید محتوای پنجرهٔ فعال و تغییر صفحه‌ها را بررسی کند و روی دکمهٔ مربوط بزند. این سرویس در این برنامه برای اجرای همین فرایند استفاده می‌شود."
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
                    Text("نمایش پنجرهٔ پیشرفت هنگام بستن برنامه‌ها")
                    Text(
                        "نمایش پنجرهٔ پیشرفت برای اطلاع از روند عملیات فعال است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("مجوز دسترس‌پذیری: ${if (accessibilityEnabled) "فعال" else "غیرفعال"}")
                    Text("نمایش روی برنامه‌های دیگر: ${if (overlayPermissionGranted) "فعال" else "غیرفعال"}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("بستن") }
            }
        )
    }
}

@Composable
private fun MemoryCard(
    usedBytes: Long,
    totalBytes: Long,
    freeBytes: Long,
    onRefresh: () -> Unit
) {
    val ratio = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    Card(
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = 8.dp.toPx()
                    drawArc(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = width, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = MaterialTheme.colorScheme.primary,
                        startAngle = -90f,
                        sweepAngle = ratio * 360f,
                        useCenter = false,
                        style = Stroke(width = width, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(ratio * 100).toInt()}%", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("RAM", style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("حافظهٔ آزاد", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatMemory(freeBytes), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${formatMemory(usedBytes)} استفاده‌شده از ${formatMemory(totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "به‌روزرسانی RAM")
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
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
                    if (app.isSystem) "برنامهٔ سیستمی" else if (app.isRunning) "در حال اجرا" else "برنامهٔ کاربری",
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
    return if (gb >= 1.0) String.format(Locale.US, "%.1f GB", gb)
    else String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
}
