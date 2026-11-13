package com.shayan.phone

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

@Composable
fun AudioDiagnosticScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("آماده‌ی آزمایش") }
    var error by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    var peak by remember { mutableFloatStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    val permissionGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(permissionGranted) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        status = if (granted) "مجوز میکروفون صادر شد؛ آماده‌ی آزمایش" else "مجوز میکروفون رد شد"
    }
    val audioManager = context.getSystemService(AudioManager::class.java)
    val inCallMode = audioManager?.mode == AudioManager.MODE_IN_CALL ||
        audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION

    fun stopTest() {
        job?.cancel()
        job = null
        running = false
        level = 0f
        status = "آزمایش متوقف شد"
    }

    DisposableEffect(Unit) {
        onDispose { job?.cancel() }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت") }
            Text("آزمایش صدا", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 10.dp))
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("اطلاعات دستگاه", style = MaterialTheme.typography.titleMedium)
                Text("سازنده: ${Build.MANUFACTURER}")
                Text("مدل: ${Build.MODEL}")
                Text("اندروید: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                Text("منبع ورودی: میکروفون اصلی (MIC)")
                Text("فرمت: PCM 16-bit، تک‌کاناله، 44.1 kHz")
            }
        }
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("وضعیت آزمایش", style = MaterialTheme.typography.titleMedium)
                Text(status)
                if (error.isNotBlank()) Text("جزئیات خطا: $error", color = MaterialTheme.colorScheme.error)
                Text("سطح لحظه‌ای ورودی: ${(level * 100).toInt()}٪")
                LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth())
                Text("بیشینه‌ی ثبت‌شده: ${(peak * 100).toInt()}٪")
                if (inCallMode) {
                    Text(
                        "حالت تماس فعال است. برای سنجش مستقل میکروفون، تماس را پایان بده و دوباره آزمایش کن.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (!hasPermission) {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("درخواست مجوز میکروفون")
                    }
                } else if (!running) {
                    Button(
                        onClick = {
                            error = ""
                            if (inCallMode) {
                                status = "آزمایش در حالت تماس اجرا نشد"
                                error = "برای جلوگیری از تداخل، آزمایش را پس از پایان تماس اجرا کن."
                            } else {
                                try {
                                    val sampleRate = 44100
                                    val minBuffer = AudioRecord.getMinBufferSize(
                                        sampleRate,
                                        AudioFormat.CHANNEL_IN_MONO,
                                        AudioFormat.ENCODING_PCM_16BIT
                                    )
                                    if (minBuffer <= 0) {
                                        error = "getMinBufferSize=$minBuffer"
                                        status = "راه‌اندازی ناموفق"
                                    } else {
                                        val recorder = AudioRecord(
                                            MediaRecorder.AudioSource.MIC,
                                            sampleRate,
                                            AudioFormat.CHANNEL_IN_MONO,
                                            AudioFormat.ENCODING_PCM_16BIT,
                                            minBuffer * 2
                                        )
                                        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                                            recorder.release()
                                            error = "AudioRecord.STATE_UNINITIALIZED"
                                            status = "میکروفون قابل راه‌اندازی نیست"
                                        } else {
                                            recorder.startRecording()
                                            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                                                recorder.release()
                                                error = "RECORDSTATE_STOPPED"
                                                status = "شروع دریافت صدا ناموفق بود"
                                            } else {
                                                running = true
                                                status = "در حال دریافت ورودی میکروفون؛ صحبت کن"
                                                job = scope.launch(Dispatchers.IO) {
                                                    val buffer = ShortArray(1024)
                                                    try {
                                                        while (isActive) {
                                                            val count = recorder.read(
                                                                buffer, 0, buffer.size,
                                                                AudioRecord.READ_NON_BLOCKING
                                                            )
                                                            if (count > 0) {
                                                                var sum = 0.0
                                                                var localPeak = 0
                                                                for (i in 0 until count) {
                                                                    val value = kotlin.math.abs(buffer[i].toInt())
                                                                    sum += value.toDouble() * value.toDouble()
                                                                    if (value > localPeak) localPeak = value
                                                                }
                                                                val rms = (sqrt(sum / count) / 32768.0)
                                                                    .toFloat().coerceIn(0f, 1f)
                                                                val maxValue = (localPeak / 32768f).coerceIn(0f, 1f)
                                                                withContext(Dispatchers.Main) {
                                                                    level = rms
                                                                    if (maxValue > peak) peak = maxValue
                                                                }
                                                            } else if (count < 0) {
                                                                withContext(Dispatchers.Main) {
                                                                    error = "AudioRecord.read=$count"
                                                                    status = "خطا هنگام خواندن ورودی"
                                                                }
                                                                break
                                                            }
                                                            delay(70)
                                                        }
                                                    } finally {
                                                        runCatching { recorder.stop() }
                                                        recorder.release()
                                                        withContext(Dispatchers.Main) {
                                                            running = false
                                                            level = 0f
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (t: Throwable) {
                                    status = "راه‌اندازی میکروفون با خطا مواجه شد"
                                    error = "${t.javaClass.simpleName}: ${t.message ?: "بدون توضیح"}"
                                    running = false
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null)
                        Spacer(Modifier.height(2.dp))
                        Text("شروع آزمایش")
                    }
                } else {
                    OutlinedButton(onClick = { stopTest() }) { Text("توقف آزمایش") }
                }
            }
        }
        Text(
            "این صفحه فقط ورودی میکروفون را به‌صورت زنده اندازه‌گیری می‌کند؛ فایل صوتی ذخیره نمی‌شود و صدای تماس‌گیرنده‌ی دیگر را دریافت نمی‌کند.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        OutlinedButton(onClick = { peak = 0f; level = 0f; error = ""; status = "آماده‌ی آزمایش" }, enabled = !running) {
            Text("پاک‌کردن نتیجه")
        }
    }
}
