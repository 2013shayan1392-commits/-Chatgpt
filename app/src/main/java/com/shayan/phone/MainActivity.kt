package com.shayan.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class Person(val name: String, val number: String)
data class Recent(val name: String, val number: String, val type: Int)

class MainActivity : ComponentActivity() {
 private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
 private val role = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  perms.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECORD_AUDIO))
  setContent { PhoneApp(this) { requestDefaultDialer() } }
 }
 private fun requestDefaultDialer() {
  val rm = getSystemService(RoleManager::class.java)
  if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) role.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
  else Toast.makeText(this, "این برنامه از قبل تلفن پیش‌فرض است یا نقش در دسترس نیست", Toast.LENGTH_SHORT).show()
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PhoneApp(ctx: Context, onSetDefault: () -> Unit) {
 var tab by remember { mutableIntStateOf(0) }
 var number by remember { mutableStateOf("") }
 var search by remember { mutableStateOf("") }
 var contacts by remember { mutableStateOf(loadContacts(ctx)) }
 var recents by remember { mutableStateOf(loadRecents(ctx)) }
 val isDefault = ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == ctx.packageName
 MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
  Scaffold(topBar = { TopAppBar(title = { Text("تلفن", fontWeight = FontWeight.SemiBold) }, actions = { TextButton(onClick = onSetDefault) { Text(if (isDefault) "تلفن پیش‌فرض ✓" else "تنظیم به‌عنوان پیش‌فرض") } }) },
   bottomBar = { NavigationBar {
    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Dialpad, null) }, label = { Text("شماره‌گیر") })
    NavigationBarItem(selected = tab == 1, onClick = { contacts = loadContacts(ctx); tab = 1 }, icon = { Icon(Icons.Default.Contacts, null) }, label = { Text("مخاطبین") })
    NavigationBarItem(selected = tab == 2, onClick = { recents = loadRecents(ctx); tab = 2 }, icon = { Icon(Icons.Default.History, null) }, label = { Text("تماس‌ها") })
   } }) { pad ->
   Column(Modifier.fillMaxSize().padding(pad).padding(18.dp)) {
    when(tab) {
     0 -> {
      Text("شماره‌گیری", style = MaterialTheme.typography.titleLarge)
      Spacer(Modifier.height(14.dp))
      OutlinedTextField(value = number, onValueChange = { number = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("شماره تلفن") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), trailingIcon = { IconButton(onClick = { number = number.dropLast(1) }) { Icon(Icons.Default.Backspace, null) } })
      Spacer(Modifier.height(14.dp))
      listOf("1","2","3","4","5","6","7","8","9","*","0","#").chunked(3).forEach { row ->
       Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        row.forEach { key -> FilledTonalButton(onClick = { number += key }, modifier = Modifier.size(72.dp), shape = CircleShape, contentPadding = PaddingValues(0.dp)) { Text(key, style = MaterialTheme.typography.headlineMedium) } }
       }; Spacer(Modifier.height(8.dp))
      }
      Button(onClick = {
       if (number.isNotBlank()) {
        val i = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)))
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) ctx.startActivity(i)
        else Toast.makeText(ctx, "مجوز تماس داده نشده است", Toast.LENGTH_SHORT).show()
       }
      }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) { Icon(Icons.Default.Call, null); Spacer(Modifier.width(8.dp)); Text("تماس") }
     }
     1 -> {
      OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("جست‌وجوی مخاطب") }, leadingIcon = { Icon(Icons.Default.Search, null) })
      LazyColumn { items(contacts.filter { it.name.contains(search, true) || it.number.contains(search) }) { p ->
       ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text(p.number) }, leadingContent = { Icon(Icons.Default.AccountCircle, null) },
        trailingContent = { IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(p.number)))) }) { Icon(Icons.Default.Call, null) } })
       HorizontalDivider()
      } }
     }
     else -> LazyColumn { items(recents) { c ->
      ListItem(headlineContent = { Text(c.name) }, supportingContent = { Text(c.number) },
       leadingContent = { Icon(if (c.type == CallLog.Calls.INCOMING_TYPE) Icons.Default.CallReceived else Icons.Default.CallMade, null) },
       trailingContent = { IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(c.number)))) }) { Icon(Icons.Default.Call, null) } })
      HorizontalDivider()
     } }
    }
   }
  }
 }
}

fun loadContacts(ctx: Context): List<Person> {
 if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
 val out = mutableListOf<Person>()
 runCatching { ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")?.use { c -> while(c.moveToNext()) out.add(Person(c.getString(0) ?: "", c.getString(1) ?: "")) } }
 return out.distinctBy { it.number }
}
fun loadRecents(ctx: Context): List<Recent> {
 if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return emptyList()
 val out = mutableListOf<Recent>()
 runCatching { ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE), null, null, CallLog.Calls.DATE + " DESC")?.use { c -> while(c.moveToNext()) out.add(Recent(c.getString(0) ?: "ناشناس", c.getString(1) ?: "", c.getInt(2))) } }
 return out
}
