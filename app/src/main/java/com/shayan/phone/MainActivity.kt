package com.shayan.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class Person(val name: String, val number: String)
data class Recent(val name: String, val number: String, val type: Int)

class MainActivity : ComponentActivity() {
    private var defaultDialerState = mutableStateOf(false)
    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        refreshDefaultDialer()
        if (result.resultCode == RESULT_OK && defaultDialerState.value) {
            Toast.makeText(this, "Shayan Phone به‌عنوان تلفن پیش‌فرض انتخاب شد", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "انتخاب انجام نشد. بررسی: ${dialerEligibilityReport()}", Toast.LENGTH_LONG).show()
        }
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshDefaultDialer()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            ShayanPhoneTheme {
                PhoneApp(
                    context = this,
                    isDefaultDialer = defaultDialerState.value,
                    onDefault = { requestDialerRole() },
                    onContactsPermission = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS)) },
                    onHistoryPermission = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG)) },
                    onCallPermission = { permissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE)) }
                )
            }
        }
    }
    override fun onResume() {
        super.onResume()
        refreshDefaultDialer()
    }

    private fun refreshDefaultDialer() {
        val rm = getSystemService(RoleManager::class.java)
        defaultDialerState.value = rm?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    }

    private fun requestDialerRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            Toast.makeText(this, "نقش تلفن پیش‌فرض در این دستگاه موجود نیست", Toast.LENGTH_LONG).show()
            return
        }
        if (rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
            refreshDefaultDialer()
            Toast.makeText(this, "برنامه تلفن پیش‌فرض است", Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        }.onFailure {
            Toast.makeText(this, "خطا در درخواست نقش تلفن: ${it.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun dialerEligibilityReport(): String {
        val pm = packageManager
        fun hasOwnActivity(intent: Intent): Boolean = pm.queryIntentActivities(
            intent, PackageManager.MATCH_DEFAULT_ONLY
        ).any { it.activityInfo.packageName == packageName }

        val dial = hasOwnActivity(Intent(Intent.ACTION_DIAL))
        val tel = hasOwnActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:123")))
        val service = pm.queryIntentServices(
            Intent("android.telecom.InCallService").setPackage(packageName), PackageManager.GET_META_DATA
        ).firstOrNull { it.serviceInfo.packageName == packageName }?.serviceInfo
        val serviceFound = service != null
        val permissionOk = service?.permission == "android.permission.BIND_INCALL_SERVICE"
        val exportedOk = service?.exported == true
        val inCallUi = service?.metaData?.getBoolean("android.telecom.IN_CALL_SERVICE_UI", false) == true
        val carModeUi = service?.metaData?.getBoolean("android.telecom.IN_CALL_SERVICE_CAR_MODE_UI", false) == true
        val roleAvailable = getSystemService(RoleManager::class.java)
            ?.isRoleAvailable(RoleManager.ROLE_DIALER) == true
        return "DIAL=$dial, tel=$tel, Service=$serviceFound, Permission=$permissionOk, Exported=$exportedOk, UI=$inCallUi, CarUI=$carModeUi, Role=$roleAvailable"
    }
}

@Composable
fun ShayanPhoneTheme(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) androidx.compose.material3.dynamicDarkColorScheme(context)
        else androidx.compose.material3.dynamicLightColorScheme(context)
    } else if (darkTheme) {
        darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFFB8C7FF),
            onPrimary = androidx.compose.ui.graphics.Color(0xFF14264F),
            secondary = androidx.compose.ui.graphics.Color(0xFFBEC6DC),
            background = androidx.compose.ui.graphics.Color(0xFF10131A),
            surface = androidx.compose.ui.graphics.Color(0xFF191E28),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFF252C39)
        )
    } else {
        lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF405F91),
            secondary = androidx.compose.ui.graphics.Color(0xFF565F71),
            background = androidx.compose.ui.graphics.Color(0xFFF8F9FF),
            surface = androidx.compose.ui.graphics.Color(0xFFF8F9FF),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE1E6F0)
        )
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

@Composable
fun PhoneApp(
    context: Context,
    isDefaultDialer: Boolean,
    onDefault: () -> Unit,
    onContactsPermission: () -> Unit,
    onHistoryPermission: () -> Unit,
    onCallPermission: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var number by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf(emptyList<Person>()) }
    var recents by remember { mutableStateOf(emptyList<Recent>()) }
    val activeCall = ActiveCall.current
    LaunchedEffect(tab) {
        if (tab == 1 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) contacts = loadContacts(context)
        if (tab == 2 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) recents = loadRecents(context)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBarPhone(
                isDefault = isDefaultDialer,
                onDefault = {
                    onDefault()
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Dialpad, null) }, label = { Text("شماره‌گیر") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1; if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) onContactsPermission() }, icon = { Icon(Icons.Default.Contacts, null) }, label = { Text("مخاطبین") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2; if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) onHistoryPermission() }, icon = { Icon(Icons.Default.History, null) }, label = { Text("تماس‌ها") })
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (activeCall != null) InCallPanel(activeCall, onFinish = { ActiveCall.current = null })
            else when (tab) {
                0 -> DialerScreen(number, { number = it }, {
                    if (number.isBlank()) return@DialerScreen
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))))
                    } else onCallPermission()
                })
                1 -> ContactsScreen(search, { search = it }, contacts) { number = it; tab = 0 }
                else -> HistoryScreen(recents) { number = it; tab = 0 }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarPhone(isDefault: Boolean, onDefault: () -> Unit) {
    TopAppBar(
        title = { Column { Text("تلفن", fontWeight = FontWeight.Bold); Text("SHAYAN PHONE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) } },
        actions = {
            TextButton(onClick = onDefault) {
                Icon(if (isDefault) Icons.Default.Verified else Icons.Default.Settings, null)
                Spacer(Modifier.width(5.dp))
                Text(if (isDefault) "پیش‌فرض" else "تنظیم")
            }
        }
    )
}

@Composable
fun DialerScreen(number: String, onNumber: (String) -> Unit, onCall: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("شماره‌گیری", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        OutlinedTextField(
            value = number, onValueChange = onNumber, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp, max = 60.dp),
            placeholder = { Text("شماره تلفن") }, singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            trailingIcon = { IconButton(onClick = { onNumber(number.dropLast(1)) }) { Icon(Icons.Default.Backspace, null) } }
        )
        val keys = listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to "")
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            keys.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { (key, letters) ->
                        Column(
                            Modifier.padding(2.dp).size(68.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .clickable { onNumber(number + key) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                        ) {
                            Text(key, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                            if (letters.isNotEmpty()) Text(letters, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
        FilledIconButton(
            onClick = onCall,
            modifier = Modifier.size(60.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF35A853))
        ) {
            Icon(Icons.Default.Call, "تماس", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun ContactsScreen(search: String, onSearch: (String) -> Unit, contacts: List<Person>, onCall: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("مخاطبین", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(search, onSearch, Modifier.fillMaxWidth(), placeholder = { Text("جست‌وجوی نام یا شماره") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        LazyColumn {
            items(contacts.filter { it.name.contains(search, true) || it.number.contains(search) }) { p ->
                ListItem(
                    headlineContent = { Text(p.name) }, supportingContent = { Text(p.number) },
                    leadingContent = { Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(34.dp)) },
                    trailingContent = { IconButton(onClick = { onCall(p.number) }) { Icon(Icons.Default.Call, null) } },
                    modifier = Modifier.clickable { onCall(p.number) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun HistoryScreen(recents: List<Recent>, onCall: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("تماس‌های اخیر", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        LazyColumn {
            items(recents) { c ->
                ListItem(
                    headlineContent = { Text(c.name) }, supportingContent = { Text(c.number) },
                    leadingContent = { Icon(if (c.type == CallLog.Calls.INCOMING_TYPE) Icons.Default.CallReceived else Icons.Default.CallMade, null) },
                    trailingContent = { IconButton(onClick = { onCall(c.number) }) { Icon(Icons.Default.Call, null) } }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun InCallPanel(call: Call, onFinish: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var state by remember { mutableIntStateOf(call.state) }
    var speaker by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var keypad by remember { mutableStateOf(false) }
    var digits by remember { mutableStateOf("") }
    DisposableEffect(call) {
        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call, s: Int) { state = s; if (s == Call.STATE_DISCONNECTED) onFinish() }
        }
        call.registerCallback(callback)
        onDispose { call.unregisterCallback(callback) }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        Text(
            when (state) { Call.STATE_RINGING -> "تماس ورودی"; Call.STATE_DIALING -> "در حال تماس…"; Call.STATE_HOLDING -> "در انتظار"; else -> "تماس فعال" },
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary
        )
        Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(92.dp), tint = MaterialTheme.colorScheme.primary)
        Text(call.details.handle?.schemeSpecificPart ?: "شماره ناشناس", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (state == Call.STATE_RINGING) {
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                FilledIconButton(onClick = { call.reject(false, null) }, modifier = Modifier.size(68.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.CallEnd, "رد تماس") }
                FilledIconButton(onClick = { call.answer(VideoProfile.STATE_AUDIO_ONLY) }, modifier = Modifier.size(68.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF4F9B70))) { Icon(Icons.Default.Call, "پاسخ") }
            }
        } else {
            if (keypad) {
                Text(digits, style = MaterialTheme.typography.titleLarge)
                listOf("1","2","3","4","5","6","7","8","9","*","0","#").chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { k -> FilledTonalButton(onClick = { digits += k; call.playDtmfTone(k[0]); call.stopDtmfTone() }, modifier = Modifier.size(62.dp), shape = CircleShape) { Text(k) } }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    CallControl("بلندگو", if (speaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown) {
                        speaker = !speaker
                        ActiveCall.service?.setAudioRoute(if (speaker) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)
                    }
                    CallControl("بی‌صدا", if (muted) Icons.Default.MicOff else Icons.Default.Mic) { muted = !muted; ActiveCall.service?.setMuted(muted) }
                    CallControl("صفحه‌کلید", Icons.Default.Dialpad) { keypad = true }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    CallControl("انتظار", Icons.Default.Pause) { if (state == Call.STATE_HOLDING) call.unhold() else call.hold() }
                    CallControl("افزودن تماس", Icons.Default.PersonAdd) {
                        call.hold()
                        context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
            FilledIconButton(onClick = { call.disconnect() }, modifier = Modifier.size(70.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.CallEnd, "پایان تماس", modifier = Modifier.size(30.dp)) }
        }
    }
}

@Composable
fun CallControl(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp).clickable(onClick = onClick)) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(62.dp), shape = CircleShape) { Icon(icon, null) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

fun loadContacts(ctx: Context): List<Person> {
    val out = mutableListOf<Person>()
    runCatching {
        ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")?.use { c ->
            while (c.moveToNext()) out.add(Person(c.getString(0) ?: "", c.getString(1) ?: ""))
        }
    }
    return out.distinctBy { it.number }
}
fun loadRecents(ctx: Context): List<Recent> {
    val out = mutableListOf<Recent>()
    runCatching {
        ctx.contentResolver.query(CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE),
            null, null, CallLog.Calls.DATE + " DESC")?.use { c ->
            while (c.moveToNext()) out.add(Recent(c.getString(0) ?: "ناشناس", c.getString(1) ?: "", c.getInt(2)))
        }
    }
    return out
}
