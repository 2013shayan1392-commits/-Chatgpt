package com.shayan.apphub

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class TargetApp(
    val title: String,
    val subtitle: String,
    val packageName: String,
    val storeUrl: String,
    val icon: ImageVector,
    val accent: Color,
    val details: String
)

private val targetApps = listOf(
    TargetApp(
        title = "Brevent",
        subtitle = "مدیریت اجرای برنامه‌ها در پس‌زمینه",
        packageName = "me.piebridge.brevent",
        storeUrl = "https://play.google.com/store/apps/details?id=me.piebridge.brevent",
        icon = Icons.Rounded.Security,
        accent = Color(0xFF5578C7),
        details = "نسخهٔ اصلی Brevent با تنظیمات، سرویس و مجوزهای خودش اجرا می‌شود. در Android 10 معمولاً USB debugging باید فعال بماند."
    ),
    TargetApp(
        title = "KillApps",
        subtitle = "بستن برنامه‌های در حال اجرا",
        packageName = "com.tafayor.killall",
        storeUrl = "https://play.google.com/store/apps/details?id=com.tafayor.killall",
        icon = Icons.Rounded.Apps,
        accent = Color(0xFF258A79),
        details = "نسخهٔ اصلی KillApps با سرویس دسترس‌پذیری و تنظیمات خودش اجرا می‌شود. مجوزهای لازم را فقط داخل خود برنامه بررسی و فعال کن."
    )
)

class MainActivity : ComponentActivity() {
    private var installedPackages by mutableStateOf<Set<String>>(emptySet())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshInstalledApps()
        setContent {
            AppHubScreen(
                installedPackages = installedPackages,
                deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidLabel = "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
                onOpen = ::openOrInstall
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshInstalledApps()
    }

    private fun refreshInstalledApps() {
        installedPackages = targetApps
            .filter { isPackageInstalled(it.packageName) }
            .map { it.packageName }
            .toSet()
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun openOrInstall(app: TargetApp) {
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            try {
                startActivity(launchIntent)
            } catch (_: ActivityNotFoundException) {
                openStorePage(app.storeUrl)
            }
        } else {
            openStorePage(app.storeUrl)
        }
    }

    private fun openStorePage(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // There is no browser or store handler available on this device.
        }
    }
}

@Composable
private fun AppHubScreen(
    installedPackages: Set<String>,
    deviceLabel: String,
    androidLabel: String,
    onOpen: (TargetApp) -> Unit
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = Color(0xFFB7C8FF),
            secondary = Color(0xFF9ED5C9),
            background = Color(0xFF10141C),
            surface = Color(0xFF191F2A),
            surfaceVariant = Color(0xFF252D3A)
        )
        else -> lightColorScheme(
            primary = Color(0xFF365B9B),
            secondary = Color(0xFF287D70),
            background = Color(0xFFF5F7FC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE8EDF6)
        )
    }

    MaterialTheme(colorScheme = colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
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
                        text = "SHAYAN APP HUB",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "مرکز برنامه‌ها",
                        fontSize = 29.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "هر ابزار را جداگانه و در محیط اصلی خودش اجرا کن.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(25.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "جداسازی واقعی برنامه‌ها",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "این هاب فقط اجراکننده است؛ Brevent و KillApps هویت اندرویدی، امضا، تنظیمات و فرایند مستقل خودشان را حفظ می‌کنند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                targetApps.forEach { app ->
                    AppCard(
                        app = app,
                        installed = app.packageName in installedPackages,
                        onClick = { onOpen(app) }
                    )
                }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            text = "اطلاعات دستگاه",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = deviceLabel,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = androidLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "برای حفظ نسخهٔ اصلی، هر برنامه باید یک‌بار جداگانه نصب باشد. اگر نصب نباشد، دکمهٔ همان کارت صفحهٔ رسمی دریافتش را باز می‌کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AppCard(
    app: TargetApp,
    installed: Boolean,
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
                    color = app.accent.copy(alpha = 0.13f),
                    modifier = Modifier.size(55.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(
                            imageVector = app.icon,
                            contentDescription = app.title,
                            tint = app.accent,
                            modifier = Modifier.size(29.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.title,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = app.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = if (installed) Color(0xFF24936F) else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (installed) "نصب‌شده" else "نصب‌نشده",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = app.details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = if (installed) Icons.Rounded.Launch else Icons.Rounded.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (installed) "باز کردن ${app.title}" else "دریافت نسخهٔ اصلی")
            }
        }
    }
}
