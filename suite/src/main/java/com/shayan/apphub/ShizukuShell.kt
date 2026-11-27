package com.shayan.apphub

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun execute(context: Context, script: String, onComplete: (ShellResult) -> Unit) {
        if (!hasPermission()) {
            onComplete(ShellResult(-1, "", "Shizuku permission is not granted."))
            return
        }

        val args = Shizuku.UserServiceArgs(
            ComponentName(context, ShellUserService::class.java)
        )
            .daemon(false)
            .processNameSuffix("suite_shell")
            .debuggable(false)
            .version(2)

        val mainHandler = Handler(Looper.getMainLooper())
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val remote = ICommandService.Stub.asInterface(binder)
                if (remote == null) {
                    mainHandler.post {
                        try {
                            Shizuku.unbindUserService(args, this, true)
                        } catch (_: Throwable) {
                        }
                        onComplete(ShellResult(-1, "", "Shizuku UserService returned an invalid binder."))
                    }
                    return
                }

                Thread {
                    val result = try {
                        parseResult(remote.execute(script))
                    } catch (error: Throwable) {
                        ShellResult(-1, "", error.message ?: "Remote shell execution failed.")
                    }

                    mainHandler.post {
                        try {
                            Shizuku.unbindUserService(args, this, true)
                        } catch (_: Throwable) {
                        }
                        onComplete(result)
                    }
                }.start()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                mainHandler.post {
                    onComplete(ShellResult(-1, "", "Shizuku UserService disconnected."))
                }
            }
        }

        try {
            if (Shizuku.getVersion() < 10) {
                onComplete(ShellResult(-1, "", "Shizuku API 10 or newer is required for UserService."))
                return
            }
            Shizuku.bindUserService(args, connection)
        } catch (error: Throwable) {
            onComplete(ShellResult(-1, "", error.message ?: "Could not bind Shizuku UserService."))
        }
    }

    private fun parseResult(raw: String): ShellResult {
        val firstBreak = raw.indexOf('\n')
        if (firstBreak < 0) return ShellResult(-1, "", raw)
        val exitCode = raw.substring(0, firstBreak).trim().toIntOrNull() ?: -1
        val body = raw.substring(firstBreak + 1)
        val marker = "\n---SHAYAN_STDERR---\n"
        val split = body.indexOf(marker)
        return if (split >= 0) {
            ShellResult(exitCode, body.substring(0, split).trim(), body.substring(split + marker.length).trim())
        } else {
            ShellResult(exitCode, body.trim(), "")
        }
    }
}
