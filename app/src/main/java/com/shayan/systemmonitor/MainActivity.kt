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
private data class Snapshot(
    val cpuLoad: Int?,
    val cpuFrequencyMhz: Long?,
    val cores: Int,
    val ramTotalMb: Long,
    val ramUsedMb: Long,
    val ramPercent: Int,
    val gpuFrequencyMhz: Long?,
    val gpuLoad: Int?,
    val temperatures: List<Pair<String, Float>>
)

@Composable
private fun MonitorApp(context: Context) {
    var mode by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var previousCpu by remember { mutableStateOf(readCpuTicks()) }
    var snapshot by remember { mutableStateOf(readSnapshot(context, null)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val current = readCpuTicks()
            val usage = if (previousCpu != null && current != null) cpuPercent(previousCpu!!, current) else null
            previousCpu = current
            snapshot = readSnapshot(context, usage)
        }
    }

    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Scaffold(topBar = { MonitorTopBar(mode) { mode = it } }) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp)
            ) {
                item {
                    Text("وضعیت زنده دستگاه", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("به‌روزرسانی خودکار هر یک ثانیه", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        MetricLine("کل حافظه", "${snapshot.ramTotalMb} MB")
                        MetricLine("آزاد", "${(snapshot.ramTotalMb - snapshot.ramUsedMb).coerceAtLeast(0)} MB")
                        MetricLine("میزان استفاده", "${snapshot.ramPercent}٪", snapshot.ramPercent / 100f)
                    }
                }
                if (snapshot.gpuFrequencyMhz != null || snapshot.gpuLoad != null) {
                    item {
                        MetricCard("پردازنده گرافیکی (GPU)", Icons.Default.DeveloperBoard) {
                            snapshot.gpuLoad?.let { MetricLine("میزان استفاده", "$it٪", it / 100f) }
                            snapshot.gpuFrequencyMhz?.let { MetricLine("فرکانس", "$it MHz") }
                        }
                    }
                }
                if (snapshot.temperatures.isNotEmpty()) {
                    item {
                        MetricCard("دمای سخت‌افزار", Icons.Default.Thermostat) {
                            snapshot.temperatures.forEach { (name, value) ->
                                MetricLine(name, String.format(Locale.US, "%.1f °C", value))
                            }
                        }
                    }
                }
                item {
                    Text(
                        "اطلاعات فقط از مسیرهای مجاز اندروید و فایل‌های قابل‌خواندن بدون روت دریافت می‌شوند.",
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
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
        progress?.let { LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) }
    }
}

private fun readSnapshot(context: Context, cpuLoad: Int?): Snapshot {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memory = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(memory)
    val total = memory.totalMem / (1024 * 1024)
    val available = memory.availMem / (1024 * 1024)
    val used = (total - available).coerceAtLeast(0)
    val gpu = readGpuMetrics()
    return Snapshot(
        cpuLoad = cpuLoad,
        cpuFrequencyMhz = readCpuFrequency(),
        cores = Runtime.getRuntime().availableProcessors(),
        ramTotalMb = total,
        ramUsedMb = used,
        ramPercent = if (total > 0) ((used * 100) / total).toInt().coerceIn(0, 100) else 0,
        gpuFrequencyMhz = gpu.first,
        gpuLoad = gpu.second,
        temperatures = readTemperatures(context)
    )
}

private fun readCpuTicks(): CpuTicks? {
    return try {
        val line = File("/proc/stat").bufferedReader().use { it.readLine() } ?: return null
        val values = line.trim().split(Regex("\\s+")).drop(1).map { it.toLong() }
        if (values.size < 4) return null
        CpuTicks(values.sum(), values[3] + (values.getOrNull(4) ?: 0L))
    } catch (_: Exception) {
        null
    }
}

private fun cpuPercent(old: CpuTicks, now: CpuTicks): Int? {
    val total = now.total - old.total
    val idle = now.idle - old.idle
    if (total <= 0) return null
    return (((total - idle).toDouble() / total) * 100).toInt().coerceIn(0, 100)
}

private fun readCpuFrequency(): Long? {
    val cpuRoot = File("/sys/devices/system/cpu")
    val values = cpuRoot.listFiles()?.filter { it.name.matches(Regex("cpu[0-9]+")) }
        ?.mapNotNull { cpu ->
            listOf("scaling_cur_freq", "cpuinfo_cur_freq").firstNotNullOfOrNull { name ->
                readLong(File(cpu, "cpufreq/$name"))
            }?.div(1000)
        }?.filter { it > 0 } ?: emptyList()
    return values.takeIf { it.isNotEmpty() }?.average()?.toLong()
}

private fun readLong(file: File): Long? = try {
    if (file.canRead()) file.readText().trim().toLongOrNull() else null
} catch (_: Exception) { null }

private fun readGpuMetrics(): Pair<Long?, Int?> {
    val roots = listOf(File("/sys/class/devfreq"), File("/sys/devices/platform"))
    var frequency: Long? = null
    var load: Int? = null
    roots.forEach { root ->
        val dirs = try {
            root.walkTopDown().maxDepth(4)
                .filter { it.isDirectory && (it.name.contains("gpu", true) || it.name.contains("mali", true) || it.name.contains("adreno", true)) }
                .take(20).toList()
        } catch (_: Exception) { emptyList() }
        dirs.forEach { dir ->
            if (frequency == null) {
                listOf("cur_freq", "gpu_cur_freq").firstNotNullOfOrNull { readLong(File(dir, it)) }
                    ?.let { frequency = it / 1_000_000 }
            }
            if (load == null) {
                listOf("load", "gpu_busy_percentage", "busy").firstNotNullOfOrNull { readLong(File(dir, it)) }
                    ?.let { load = it.toInt().coerceIn(0, 100) }
            }
        }
    }
    return frequency to load
}

private fun readTemperatures(context: Context): List<Pair<String, Float>> {
    val result = mutableListOf<Pair<String, Float>>()
    try {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tenths = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        if (tenths > 0) result += "باتری" to (tenths / 10f)
    } catch (_: Exception) { }
    try {
        File("/sys/class/thermal").listFiles()
            ?.filter { it.name.matches(Regex("thermal_zone[0-9]+")) }
            ?.forEach { zone ->
                val raw = readLong(File(zone, "temp")) ?: return@forEach
                val celsius = when {
                    raw in 1..1000 -> raw.toFloat()
                    raw in 1001..200000 -> raw / 1000f
                    else -> return@forEach
                }
                val type = try { File(zone, "type").takeIf { it.canRead() }?.readText()?.trim() } catch (_: Exception) { null }
                val label = type?.takeIf { it.isNotBlank() } ?: "حسگر حرارتی"
                if (result.none { it.first == label }) result += label to celsius
            }
    } catch (_: Exception) { }
    return result
}
