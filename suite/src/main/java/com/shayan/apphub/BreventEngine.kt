package com.shayan.apphub

/**
 * Brevent-style feature core.
 * This first implementation manages Android background-execution AppOps.
 * It does not claim to reproduce Brevent's proprietary service engine.
 */
object BreventEngine {
    fun restrictScript(packages: Set<String>): String {
        require(packages.isNotEmpty()) { "No applications selected." }
        val commands = packages.sorted().flatMap { packageName ->
            require(isSafePackageName(packageName)) { "Invalid package name." }
            listOf(
                "cmd appops set " + packageName + " RUN_ANY_IN_BACKGROUND ignore",
                "am force-stop --user current " + packageName
            )
        }
        return "set -e\n" + commands.joinToString("\n")
    }

    fun restoreScript(packages: Set<String>): String {
        require(packages.isNotEmpty()) { "No applications selected." }
        val commands = packages.sorted().map { packageName ->
            require(isSafePackageName(packageName)) { "Invalid package name." }
            "cmd appops set " + packageName + " RUN_ANY_IN_BACKGROUND default"
        }
        return "set -e\n" + commands.joinToString("\n")
    }

    private fun isSafePackageName(value: String): Boolean {
        return value.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))
    }
}
