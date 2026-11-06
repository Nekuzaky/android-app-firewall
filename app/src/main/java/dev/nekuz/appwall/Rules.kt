package dev.nekuz.appwall

import android.content.Context
import android.content.pm.PackageManager

object Rules {
    private const val FILE = "rules"
    private const val KEY_BLOCKED = "blocked"
    private const val KEY_ENABLED = "enabled"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun blocked(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_BLOCKED, emptySet()).orEmpty().toSet()

    fun setBlocked(context: Context, packageName: String, blocked: Boolean) {
        val updated = blocked(context).toMutableSet()
        if (blocked) updated.add(packageName) else updated.remove(packageName)
        prefs(context).edit().putStringSet(KEY_BLOCKED, updated).apply()
    }

    fun installedCount(context: Context, packages: Set<String>): Int {
        val manager = context.packageManager
        return countInstalled(packages) { name ->
            runCatching { manager.getApplicationInfo(name, PackageManager.MATCH_DISABLED_COMPONENTS) }.isSuccess
        }
    }

    fun countInstalled(packages: Set<String>, isInstalled: (String) -> Boolean): Int = packages.count(isInstalled)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
