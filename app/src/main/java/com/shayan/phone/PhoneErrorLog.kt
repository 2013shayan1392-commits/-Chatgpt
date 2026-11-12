package com.shayan.phone

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local-only diagnostics: no phone numbers, contact names, or exception messages. */
object PhoneErrorLog {
    private const val PREFS = "shayan_phone_diagnostics"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 100

    @Synchronized
    fun add(context: Context, area: String, detail: String) {
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val safeArea = area.replace('|', '/').replace('\n', ' ').take(40)
            val safeDetail = detail.replace('|', '/').replace('\n', ' ').take(500)
            val previous = prefs.getString(KEY_ENTRIES, "").orEmpty()
            val entries = (previous.split('\n').filter { it.isNotBlank() } + (stamp + " | " + safeArea + " | " + safeDetail))
                .takeLast(MAX_ENTRIES)
            prefs.edit().putString(KEY_ENTRIES, entries.joinToString("\n")).apply()
        }
    }

    fun exception(context: Context, area: String, error: Throwable) {
        val frames = error.stackTrace.take(5).joinToString(" <- ") {
            it.className.substringAfterLast('.') + "." + it.methodName + ":" + it.lineNumber
        }
        add(context, area, error.javaClass.name + ": " + frames)
    }

    fun read(context: Context): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, "").orEmpty()
            .ifBlank { "هنوز خطایی ثبت نشده است." }

    @Synchronized
    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_ENTRIES).apply()
    }

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            exception(app, "CRASH:" + thread.name, error)
            previous?.uncaughtException(thread, error)
        }
    }
}
