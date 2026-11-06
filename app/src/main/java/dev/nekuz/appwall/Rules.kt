package dev.nekuz.appwall

import android.content.Context

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

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
