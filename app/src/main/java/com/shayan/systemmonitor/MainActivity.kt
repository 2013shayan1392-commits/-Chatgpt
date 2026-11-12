package com.shayan.systemmonitor

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MonitorApp(applicationContext) }
    }
}

private enum class ThemeMode { SYSTEM, LIGHT, DARK }
private data class CpuTicks(val total: Long, val idle: Long)
private data class GpuInfo(val frequencyMhz: Long?, val loadPercent: Int?)
private data class Snapshot(
    val cpuLoad: Int?,
    val cpuFrequencyMhz: Long?,
    val cores: Int,
    val ramTotalMb: Long,
    val ramUsedMb: Long,
    val ramPercent: Int,
    val gpu: GpuInfo,
    val temperatures: List<Pair<String, Float>>
)

@Composable
private fun MonitorApp(context: Context) {
    var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var cpuLoad by remember { mutableStateOf<Int?>(null) }
    var previousTicks by remember { mutableStateOf(readCpuTicks()) }
    var snapshot by remember { mutableStateOf(readSnapshot(context, null)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val current = readCpuTicks()
            cpuLoad = if (previousTicks != null && current != null) cpuPercent(previousTicks!!, current) else null
            previousTicks = current
            snapshot = readSnapshot(context, cpuLoad)
        }
    }

    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Scaffold(topBar = { MonitorTopBar(themeMode) { themeMode = it } }) { insets ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("وضعیت زنده دستگاه", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("به‌روزرسانی خودکار هر ثانیه", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    MetricCard("پردازنده (CPU)", Icons.Default.Memory) {
                        snapshot.cpuLoad?.let { MetricLine("میزان استفاده", "$it٪", it / 100f) }
                        snapshot.cpuFrequencyMhz?.let { MetricLine("فرکانس میانگین", "$it MHz") }
                        MetricLine("تعداد هسته‌ها", snapshot.cores.toString())
                    }
                }
                item {
                    MetricCard("حافظه رم (RAM)", Icons.Default.Memory) {
                        MetricLine("مصرف‌شده", "${snapshot.ramUsedMb} MB")
                        MetricLine("ظرفیت کل", "${snapshot.ramTotalMb} MB")
                        MetricLine("آزاد", "${(snapshot.ramTotalMb - snapshot.ramUsedMb).coerceAtLeast(0)} MB")
                        MetricLine("درصد استفاده", "${snapshot.ramPercent}٪", snapshot.ramPercent / 100f)
                    }
                }
                if (snapshot.gpu.frequencyMhz != null || snapshot.gpu.loadPercent != null) {
                    item {
                        MetricCard("پردازنده گرافیکی (GPU)", Icons.Default.DeveloperBoard) {
                            snapshot.gpu.loadPercent?.let { MetricLine("میزان استفاده", "$it٪", it / 100f) }
                            snapshot.gpu.frequencyMhz?.let { MetricLine("فرکانس", "$it MHz") }
                        }
                    }
                }
                if (snapshot.temperatures.isNotEmpty()) {
                    item {
                        MetricCard("دمای سخت‌افزار", Icons.Default.Thermostat) {
                            snapshot.temperatures.forEach { (label, temp) ->
                                MetricLine(label, String.format(Locale.US, "%.1f °C", temp))
                            }
                        }
                    }
                }
                item {
                    Text(
                        "فقط اطلاعاتی نمایش داده می‌شوند که بدون روت و از مسیرهای قابل‌دسترسی سیستم خوانده شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorTopBar(mode: ThemeMode, onModeChange: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("پایشگر سخت‌افزار") },
        actions = {
            TextButton(onClick = { expanded = true }) {
                Text(when (mode) {
                    ThemeMode.SYSTEM -> "سیستم"
                    ThemeMode.LIGHT -> "روشن"
                    ThemeMode.DARK -> "تاریک"
                })
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("مطابق سیستم") }, onClick = { onModeChange(ThemeMode.SYSTEM); expanded = false })
                DropdownMenuItem(text = { Text("روشن") }, onClick = { onModeChange(ThemeMode.LIGHT); expanded = false })
                DropdownMenuItem(text = { Text("تاریک") }, onClick = { onModeChange(ThemeMode.DARK); expanded = false })
            }
        }
    )
}

@Composable
private fun MetricCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String, progress: Float? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        progress?.let {
            LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun readSnapshot(context: Context, cpuLoad: Int?): Snapshot {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memory = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(memory)
    val total = memory.totalMem / (1024 * 1024)
    val available = memory.availMem / (1024 * 1024)
    val used = (total - available).coerceAtLeast(0)
    return Snapshot(
        cpuLoad = cpuLoad,
        cpuFrequencyMhz = readCpuFrequency(),
        cores = Runtime.getRuntime().availableProcessors(),
        ramTotalMb = total,
        ramUsedMb = used,
        ramPercent = if (total > 0) ((used * 100) / total).toInt().coerceIn(0, 100) else 0,
        gpu = readGpuInfo(),
        temperatures = readTemperatures(context)
    )
}

private fun readCpuTicks(): CpuTicks? {
    return try {
        val line = File("/proc/stat").bufferedReader().use { it.readLine() } ?: return null
        val values = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (values.size < 4) return null
        CpuTicks(values.sum(), values[3] + (values.getOrNull(4) ?: 0L))
    } catch (_: Exception) { null }
}

private fun cpuPercent(old: CpuTicks, now: CpuTicks): Int? {
    val total = now.total - old.total
    val idle = now.idle - old.idle
    if (total <= 0) return null
    return (((total - idle).toDouble() / total) * 100).toInt().coerceIn(0, 100)
}

private fun readCpuFrequency(): Long? {
    val cpuRoot = File("/sys/devices/system/cpu")
    val frequencies = cpuRoot.listFiles()
        ?.filter { it.name.matches(Regex("cpu[0-9]+")) }
        ?.mapNotNull { cpu ->
            listOf("scaling_cur_freq", "cpuinfo_cur_freq").firstNotNullOfOrNull { filename ->
                readLong(File(cpu, "cpufreq/$filename"))
            }?.div(1000)?.takeIf { it > 0 }
        } ?: emptyList()
    return frequencies.takeIf { it.isNotEmpty() }?.average()?.toLong()
}

private fun readLong(file: File): Long? = try {
    if (file.canRead()) file.readText().trim().toLongOrNull() else null
} catch (_: Exception) { null }

private fun readGpuInfo(): GpuInfo {
    var frequency: Long? = null
    var load: Int? = null
    listOf(File("/sys/class/devfreq"), File("/sys/devices/platform")).forEach { root ->
        val entries = try { root.listFiles()?.take(80) ?: emptyList() } catch (_: Exception) { emptyList() }
        entries.filter { it.name.contains("gpu", true) || it.name.contains("mali", true) || it.name.contains("adreno", true) }.forEach { dir ->
            if (frequency == null) frequency = readLong(File(dir, "cur_freq"))?.div(1_000_000)
            if (load == null) {
                val raw = listOf("gpu_busy_percentage", "busy", "load").firstNotNullOfOrNull { readLong(File(dir, it)) }
                load = raw?.toInt()?.coerceIn(0, 100)
            }
        }
    }
    return GpuInfo(frequency?.takeIf { it > 0 }, load)
}

private fun readTemperatures(context: Context): List<Pair<String, Float>> {
    val values = mutableListOf<Pair<String, Float>>()
    try {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val raw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        if (raw > 0) values.add("باتری" to raw / 10f)
    } catch (_: Exception) { }
    try {
        File("/sys/class/thermal").listFiles()
            ?.filter { it.name.matches(Regex("thermal_zone[0-9]+")) }
            ?.forEach { zone ->
                val raw = readLong(File(zone, "temp")) ?: return@forEach
                val label = try { File(zone, "type").takeIf { it.canRead() }?.readText()?.trim() } catch (_: Exception) { null }
                val celsius = when {
                    raw in 1000..200000 -> raw / 1000f
                    raw in 10..2000 -> raw / 10f
                    else -> raw.toFloat()
                }
                if (celsius in 1f..120f) values.add((label?.takeIf { it.isNotBlank() } ?: zone.name) to celsius)
            }
    } catch (_: Exception) { }
    return values.distinctBy { it.first }.take(8)
}
