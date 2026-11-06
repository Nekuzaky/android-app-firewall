package dev.nekuz.appwall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val expected = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!expected || !Rules.isEnabled(context)) return
        val started = !FirewallService.otherVpnActive(context) &&
            VpnService.prepare(context) == null &&
            FirewallService.start(context)
        if (!started) Notices.showOff(context)
    }
}
