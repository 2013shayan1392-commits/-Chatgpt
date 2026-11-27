package com.shayan.apphub

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

object ShizukuShell {
    fun isBinderAlive(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            isBinderAlive() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    @Suppress("DEPRECATION")
    fun execute(script: String): ShellResult {
        check(hasPermission()) { "Shizuku binder is unavailable or permission was not granted." }
        val process = Shizuku.newProcess(arrayOf("sh", "-c", script), null, null)
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return ShellResult(exitCode, stdout.trim(), stderr.trim())
    }
}
