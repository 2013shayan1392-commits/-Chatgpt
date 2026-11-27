package com.shayan.apphub

/**
 * KillApps feature core, separate from BreventEngine.
 * Only validated third-party package IDs selected by the user are accepted.
 */
object KillAppsEngine {
    fun forceStopScript(packages: Set<String>): String {
        require(packages.isNotEmpty()) { "No applications selected." }
        val commands = packages.sorted().map { packageName ->
            require(isSafePackageName(packageName)) { "Invalid package name." }
            "if am force-stop --user current " + packageName + " >/dev/null 2>&1; then echo SHAYAN_OK:" + packageName + "; else echo SHAYAN_FAIL:" + packageName + "; fi"
        }
        return commands.joinToString("\n")
    }

    private fun isSafePackageName(value: String): Boolean {
        return value.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))
    }
}
