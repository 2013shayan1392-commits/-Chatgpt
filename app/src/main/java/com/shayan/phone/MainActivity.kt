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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Person(val name: String, val number: String, val contactId: Long, val dataId: Long)
data class Recent(val name: String, val number: String, val type: Int)

class MainActivity : ComponentActivity() {
    private var defaultDialerState = mutableStateOf(false)
    private var diagnosticState = mutableStateOf<String?>(null)
    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        refreshDefaultDialer()
        if (result.resultCode == RESULT_OK && defaultDialerState.value) {
            Toast.makeText(this, "Shayan Phone به‌عنوان تلفن پیش‌فرض انتخاب شد", Toast.LENGTH_LONG).show()
        } else {
            diagnosticState.value = "انتخاب نقش انجام نشد.\n\n${dialerEligibilityReport()}\n\nاین گزارش را کامل برای بررسی ارسال کن."
        }
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.any { it }) permissionRefresh.value++
    }
    private val permissionRefresh = mutableIntStateOf(0)
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
                    onContactsPermission = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) },
                    onHistoryPermission = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG)) },
                    onCallPermission = { permissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE)) },
                    permissionRefresh = permissionRefresh.intValue
                )
                diagnosticState.value?.let { report ->
                    AlertDialog(
                        onDismissRequest = { diagnosticState.value = null },
                        title = { Text("گزارش سازگاری تلفن پیش‌فرض") },
                        text = { Text(report, textAlign = TextAlign.Start) },
                        confirmButton = { TextButton(onClick = { diagnosticState.value = null }) { Text("متوجه شدم") } }
                    )
                }
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

        val dial = hasOwnActivity(Intent(Intent.ACTION_DIAL).addCategory(Intent.CATEGORY_DEFAULT))
        val tel = hasOwnActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:123")).addCategory(Intent.CATEGORY_DEFAULT))
        val service = pm.queryIntentServices(
            Intent("android.telecom.InCallService").setPackage(packageName), PackageManager.GET_META_DATA
        ).firstOrNull { it.serviceInfo.packageName == packageName }?.serviceInfo
        val serviceFound = service != null
        val permissionOk = service?.permission == "android.permission.BIND_INCALL_SERVICE"
        val exportedOk = service?.exported == true
        val enabledOk = service?.enabled == true
        val inCallUi = service?.metaData?.getBoolean("android.telecom.IN_CALL_SERVICE_UI", false) == true
        val carModeUi = service?.metaData?.getBoolean("android.telecom.IN_CALL_SERVICE_CAR_MODE_UI", false) == true
        val roleAvailable = getSystemService(RoleManager::class.java)
            ?.isRoleAvailable(RoleManager.ROLE_DIALER) == true
        return "DIAL=$dial\ntel=$tel\nService=$serviceFound\nPermission=$permissionOk\nExported=$exportedOk\nEnabled=$enabledOk\nUI=$inCallUi\nCarUI=$carModeUi\nRoleAvailable=$roleAvailable\nPackage=$packageName"
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
    onCallPermission: () -> Unit,
    permissionRefresh: Int
) {
    var tab by remember { mutableIntStateOf(0) }
    var number by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf(emptyList<Person>()) }
    var recents by remember { mutableStateOf(emptyList<Recent>()) }
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    var editingContact by remember { mutableStateOf<Person?>(null) }
    var pendingEditContact by remember { mutableStateOf<Person?>(null) }
    val activeCall = ActiveCall.current
    LaunchedEffect(permissionRefresh) {
        val editPending = pendingEditContact
        if (editPending != null && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            pendingEditContact = null
            editingContact = loadContacts(context).firstOrNull { it.dataId == editPending.dataId } ?: editPending
        }
        val pending = pendingCallNumber
        if (pending != null && ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            pendingCallNumber = null
            context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(pending))))
        }
    }
    LaunchedEffect(tab, permissionRefresh) {
        if ((tab == 0 || tab == 1) && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) contacts = loadContacts(context)
        if ((tab == 0 || tab == 2) && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) recents = loadRecents(context)
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
            if (tab != 0) {
                FloatingPhoneNavigationBar(
                    selectedTab = tab,
                    onSelect = { selected ->
                        tab = selected
                        if (selected == 1 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) onContactsPermission()
                        if (selected == 2 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) onHistoryPermission()
                    }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (activeCall != null) InCallPanel(activeCall, onFinish = { ActiveCall.current = null })
            else AnimatedContent(targetState = tab, label = "tab-content") { selectedTab ->
                when (selectedTab) {
                    0 -> DialerScreen(number, { number = it }, contacts, recents, onCall = {
                        if (number.isBlank()) return@DialerScreen
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                            context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))))
                        } else {
                            pendingCallNumber = number
                            onCallPermission()
                        }
                    }, onEditContact = { id -> val person = contacts.firstOrNull { it.contactId == id }; if (person != null && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) editingContact = person else if (person != null) { pendingEditContact = person; onContactsPermission() } },
                        onSelectTab = { selected ->
                            tab = selected
                            if (selected == 1 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) onContactsPermission()
                            if (selected == 2 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) onHistoryPermission()
                        })
                    1 -> ContactsScreen(search, { search = it }, contacts, onCall = { number = it; tab = 0 }, onEdit = { person -> if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) editingContact = person else { pendingEditContact = person; onContactsPermission() } })
                    else -> HistoryScreen(recents) { number = it; tab = 0 }
                }
            }
        }
    }
    editingContact?.let { person ->
        ContactEditDialog(person, onDismiss = { editingContact = null }, onSave = { name, phone ->
            val saved = updateContactDetails(context, person, name, phone)
            if (saved) { contacts = loadContacts(context); Toast.makeText(context, "مخاطب ذخیره شد", Toast.LENGTH_SHORT).show() }
            else Toast.makeText(context, "ذخیره انجام نشد؛ مجوز ویرایش مخاطبین را بررسی کن", Toast.LENGTH_LONG).show()
            editingContact = null
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun FloatingPhoneNavigationBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    val items = listOf(Triple(2, Icons.Default.History, "تماس‌ها"), Triple(1, Icons.Default.Contacts, "مخاطبین"), Triple(0, Icons.Default.Dialpad, "شماره‌گیر"))
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(30.dp)).padding(7.dp)) {
        val slotWidth = (maxWidth - 10.dp) / 3
        val selectedIndex = items.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0)
        val indicatorOffset by androidx.compose.animation.core.animateDpAsState(
            targetValue = (slotWidth + 5.dp) * selectedIndex,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
            ), label = "nav-indicator")
        Box(Modifier.offset(x = indicatorOffset).width(slotWidth).height(54.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(26.dp)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            items.forEach { (tab, icon, label) ->
                val scale by animateFloatAsState(if (selectedTab == tab) 1.08f else 1f, label = "nav-icon-scale")
                Column(Modifier.weight(1f).height(54.dp).clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(icon, null, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
                    Spacer(Modifier.height(3.dp)); Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

fun openContactEditor(context: Context, contactId: Long) {
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
    runCatching {
        context.startActivity(Intent(Intent.ACTION_EDIT, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, "امکان باز کردن ویرایشگر مخاطب وجود ندارد", Toast.LENGTH_SHORT).show()
    }
}

fun normalizePhone(value: String): String {
    val digits = value.map { ch ->
        when (ch) {
            in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
            in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
            else -> ch
        }
    }.filter { it.isDigit() }.joinToString("")
    return when {
        digits.startsWith("0098") -> "0" + digits.drop(4)
        digits.startsWith("98") && digits.length >= 12 -> "0" + digits.drop(2)
        else -> digits
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
fun DialerScreen(
    number: String,
    onNumber: (String) -> Unit,
    contacts: List<Person>,
    recents: List<Recent>,
    onCall: () -> Unit,
    onEditContact: (Long) -> Unit,
    onSelectTab: (Int) -> Unit
) {
    var sheetHeightFraction by remember { mutableFloatStateOf(0.61f) }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    val queryDigits = normalizePhone(number)
    val matching = if (number.isBlank()) emptyList() else contacts.filter { p ->
        (queryDigits.isNotEmpty() && normalizePhone(p.number).contains(queryDigits)) ||
            p.name.contains(number, ignoreCase = true)
    }.take(30)
    val targetSheetHeight = (sheetHeightFraction + if (number.isNotBlank()) 0.07f else 0f).coerceAtMost(0.90f)
    val displayedSheetHeight by animateFloatAsState(targetValue = targetSheetHeight, label = "dialer-sheet-height")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Recent calls remain visible behind the bottom dialer panel.
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)
                .padding(bottom = maxHeight * displayedSheetHeight + 8.dp)
        ) {
            if (number.isBlank()) {
                items(recents.take(50)) { c ->
                    ListItem(
                        headlineContent = { Text(c.name, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(c.number) },
                        leadingContent = { Icon(Icons.Default.Info, null) },
                        trailingContent = {
                            IconButton(onClick = { onNumber(c.number) }) {
                                Icon(Icons.Default.Call, "شماره‌گیری")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { onNumber(c.number) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
            } else if (matching.isNotEmpty()) {
                items(matching) { person ->
                    ListItem(
                        headlineContent = { Text(person.name, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(person.number) },
                        leadingContent = { Icon(Icons.Default.AccountCircle, null) },
                        trailingContent = {
                            IconButton(onClick = { onNumber(person.number) }) {
                                Icon(Icons.Default.Call, "استفاده از شماره")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { onNumber(person.number) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
            } else {
                item {
                    Text(
                        "مخاطبی با این شماره پیدا نشد",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(displayedSheetHeight)
                .background(androidx.compose.ui.graphics.Color(0xFF303030),
                    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .padding(horizontal = 22.dp)
        ) {
            Box(
                Modifier.fillMaxWidth().height(30.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, amount -> dragTotal += amount },
                            onDragEnd = {
                                sheetHeightFraction = if (dragTotal < -35f) 0.78f else if (dragTotal > 35f) 0.52f else sheetHeightFraction
                                dragTotal = 0f
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(42.dp).height(5.dp)
                    .background(androidx.compose.ui.graphics.Color(0xFF9A969D), RoundedCornerShape(8.dp)))
            }
            if (number.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().height(42.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { onNumber(number.dropLast(1)) }) {
                        Icon(Icons.Default.Backspace, "حذف رقم", tint = androidx.compose.ui.graphics.Color.LightGray)
                    }
                    Text(number, color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.End)
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                val keys = listOf(
                    "1" to "", "2" to "ABC", "3" to "DEF",
                    "4" to "GHI", "5" to "JKL", "6" to "MNO",
                    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
                    "*" to "(P)", "0" to "+", "#" to "(W)"
                )
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    keys.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { (key, letters) ->
                                Column(
                                    Modifier.weight(1f).height(62.dp).clickable { onNumber(number + key) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(key, color = androidx.compose.ui.graphics.Color(0xFFF2F0F3),
                                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Normal)
                                    if (letters.isNotEmpty()) Text(letters,
                                        color = androidx.compose.ui.graphics.Color(0xFF9D9AA0),
                                        style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(78.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            IconButton(onClick = { onNumber(number.dropLast(1)) }) {
                                Icon(Icons.Default.Backspace, "حذف رقم",
                                    tint = androidx.compose.ui.graphics.Color(0xFF9D9AA0),
                                    modifier = Modifier.size(27.dp))
                            }
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            FilledIconButton(
                                onClick = onCall,
                                modifier = Modifier.size(76.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = androidx.compose.ui.graphics.Color(0xFF55AD55)
                                )
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.Call, "تماس", modifier = Modifier.size(31.dp))
                                    Text("VoLTE", style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            IconButton(onClick = { sheetHeightFraction = if (sheetHeightFraction < 0.7f) 0.78f else 0.61f }) {
                                Icon(Icons.Default.Dialpad, "تغییر اندازه شماره‌گیر",
                                    tint = androidx.compose.ui.graphics.Color(0xFFE0DDE2),
                                    modifier = Modifier.size(27.dp))
                            }
                        }
                    }
                }
            }
            FloatingPhoneNavigationBar(
                selectedTab = 0,
                onSelect = onSelectTab
            )
        }
    }
}

@Composable
fun ContactsScreen(search: String, onSearch: (String) -> Unit, contacts: List<Person>, onCall: (String) -> Unit, onEdit: (Person) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("مخاطبین", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(search, onSearch, Modifier.fillMaxWidth(), placeholder = { Text("جست‌وجوی نام یا شماره") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        LazyColumn {
            items(contacts.filter { it.name.contains(search, true) || it.number.contains(search) }) { p ->
                ListItem(
                    headlineContent = { Text(p.name) }, supportingContent = { Text(p.number) },
                    leadingContent = { Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(34.dp)) },
                    trailingContent = { Row { IconButton(onClick = { onEdit(p) }) { Icon(Icons.Default.Edit, "ویرایش") }; IconButton(onClick = { onCall(p.number) }) { Icon(Icons.Default.Call, null) } } },
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
    val speaker = ActiveCall.audioRoute.intValue == CallAudioState.ROUTE_SPEAKER
    val muted by ActiveCall.muted
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
        val phoneNumber = call.details.handle?.schemeSpecificPart.orEmpty()
        var savedName by remember(phoneNumber) { mutableStateOf<String?>(null) }
        LaunchedEffect(phoneNumber) { savedName = withContext(Dispatchers.IO) { lookupSavedContactName(context, phoneNumber) } }
        Text(savedName ?: phoneNumber.ifBlank { "شماره ناشناس" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (savedName != null && phoneNumber.isNotBlank()) Text(phoneNumber, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
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
                        ActiveCall.service?.setAudioRoute(if (speaker) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER)
                    }
                    CallControl("بی‌صدا", if (muted) Icons.Default.MicOff else Icons.Default.Mic) { ActiveCall.setMuted(!muted) }
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

fun lookupSavedContactName(ctx: Context, number: String): String? {
    if (number.isBlank() || ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
    return runCatching {
        ctx.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null }
    }.getOrNull()
}

@Composable
fun ContactEditDialog(person: Person, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember(person.dataId) { mutableStateOf(person.name) }
    var phone by remember(person.dataId) { mutableStateOf(person.number) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("ویرایش مخاطب") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("نام مخاطب") }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, label = { Text("شماره تلفن") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), phone.trim()) }, enabled = name.isNotBlank() && phone.isNotBlank()) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

fun updateContactDetails(ctx: Context, person: Person, name: String, phone: String): Boolean {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false
    return runCatching {
        val nameValues = android.content.ContentValues().apply {
            put(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, name)
            put(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, "")
        }
        val phoneValues = android.content.ContentValues().apply {
            put(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
            put(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER, normalizePhone(phone))
        }
        val names = ctx.contentResolver.update(ContactsContract.Data.CONTENT_URI, nameValues,
            "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
            arrayOf(person.contactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE))
        val phones = ctx.contentResolver.update(ContactsContract.Data.CONTENT_URI, phoneValues,
            "${ContactsContract.Data._ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
            arrayOf(person.dataId.toString(), ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE))
        names > 0 && phones > 0
    }.getOrDefault(false)
}

fun loadContacts(ctx: Context): List<Person> {
    val out = mutableListOf<Person>()
    runCatching {
        ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone._ID),
            null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")?.use { c ->
            while (c.moveToNext()) out.add(Person(c.getString(0) ?: "", c.getString(1) ?: "", c.getLong(2), c.getLong(3)))
        }
    }
    return out.distinctBy { it.contactId to normalizePhone(it.number) }
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
