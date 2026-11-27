package com.shayan.apphub

class ShellUserService : ICommandService.Stub() {
    override fun destroy() {
        System.exit(0)
    }

    override fun execute(script: String): String {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("/system/bin/sh", "-c", script)
            )
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            exitCode.toString() + "\n" + stdout + "\n---SHAYAN_STDERR---\n" + stderr
        } catch (error: Throwable) {
            "-1\n\n---SHAYAN_STDERR---\n" + (error.message ?: "Shell command failed.")
        }
    }
}
