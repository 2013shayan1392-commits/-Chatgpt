package com.shayan.phone

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val HUAWEI_RECORDER_PACKAGE = "com.huawei.phone.recorder"

private data class HuaweiRecorderReport(
    val isHuawei: Boolean,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val androidRelease: String,
    val sdk: Int,
    val buildDisplay: String,
    val recorderInstalled: Boolean,
    val recorderVersion: String?
)

class HuaweiRecorderDiagnosticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = inspectHuaweiRecorder()
        setContent {
            ShayanPhoneTheme {
                HuaweiRecorderDiagnosticsScreen(report = report, onClose = { finish() })
            }
        }
    }

    private fun inspectHuaweiRecorder(): HuaweiRecorderReport {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val isHuawei = manufacturer.equals("HUAWEI", ignoreCase = true) ||
            brand.equals("HUAWEI", ignoreCase = true) ||
            manufacturer.equals("HONOR", ignoreCase = true) ||
            brand.equals("HONOR", ignoreCase = true)

        val recorderInfo: PackageInfo? = try {
            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    HUAWEI_RECORDER_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(HUAWEI_RECORDER_PACKAGE, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }

        return HuaweiRecorderReport(
            isHuawei = isHuawei,
            manufacturer = manufacturer,
            brand = brand,
            model = Build.MODEL.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdk = Build.VERSION.SDK_INT,
            buildDisplay = Build.DISPLAY.orEmpty(),
            recorderInstalled = recorderInfo != null,
            recorderVersion = recorderInfo?.versionName
        )
    }
}

@Composable
private fun HuaweiRecorderDiagnosticsScreen(
    report: HuaweiRecorderReport,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("بررسی سازگاری هواوی", style = MaterialTheme.typography.headlineSmall)
        Text(
            "این صفحه فقط وضعیت دستگاه و قابل‌شناسایی‌بودن ماژول رسمی ضبط هواوی را بررسی می‌کند."
        )

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (report.isHuawei) "دستگاه از خانوادهٔ Huawei / Honor است"
                    else "سازندهٔ دستگاه هواوی نیست",
                    style = MaterialTheme.typography.titleMedium
                )
                Text("سازنده: ${report.manufacturer}")
                Text("برند: ${report.brand}")
                Text("مدل: ${report.model}")
                Text("نسخهٔ اندروید: ${report.androidRelease} (API ${report.sdk})")
                Text("شناسهٔ ساخت: ${report.buildDisplay}")
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ماژول Huawei Call Recorder", style = MaterialTheme.typography.titleMedium)
                Text("شناسه: $HUAWEI_RECORDER_PACKAGE")
                if (report.recorderInstalled) {
                    Text("وضعیت: بستهٔ برنامه پیدا شد")
                    Text("نسخه: ${report.recorderVersion ?: "نامشخص"}")
                } else {
                    Text("وضعیت: بسته پیدا نشد یا سیستم اجازهٔ شناسایی آن را نداد")
                }
            }
        }

        Text(
            "توجه: پیدا شدن بسته به‌تنهایی به معنی فعال‌بودن سرویس ضبط یا سازگاری آن با نسخهٔ فعلی Phone نیست. این گزارش هیچ تماسی را ضبط نمی‌کند و هیچ تنظیم سیستمی را تغییر نمی‌دهد.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onClose) { Text("بازگشت") }
    }
}
