package dev.nekuz.appwall

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread

class FirewallService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Rules.setEnabled(this, false)
            shutDown()
            return START_NOT_STICKY
        }
        Rules.setEnabled(this, true)
        return if (applyRules()) START_STICKY else START_NOT_STICKY
    }

    override fun onRevoke() {
        Rules.setEnabled(this, false)
        shutDown()
        super.onRevoke()
    }

    override fun onDestroy() {
        closeTunnel()
        running = false
        super.onDestroy()
    }

    private fun applyRules(): Boolean {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(MTU)
            .setBlocking(true)
            .addAddress(TUNNEL_ADDRESS_V4, 32)
            .addAddress(TUNNEL_ADDRESS_V6, 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer(TUNNEL_DNS_V4)
            .addDnsServer(TUNNEL_DNS_V6)
            .addAllowedApplication(packageName)
        for (blocked in Rules.blocked(this)) {
            runCatching { builder.addAllowedApplication(blocked) }
        }
        val next = runCatching { builder.establish() }.getOrNull()
        if (next == null) {
            shutDown()
            return false
        }
        val previous = tunnel
        tunnel = next
        running = true
        startReader(next)
        runCatching { previous?.close() }
        return true
    }

    private fun startReader(descriptor: ParcelFileDescriptor) {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        thread(name = "tunnel-reader", isDaemon = true) {
            val buffer = ByteArray(MTU)
            try {
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    while (true) {
                        val length = input.read(buffer)
                        if (length < 0) break
                        if (length > 0) runCatching { record(connectivity, buffer, length) }
                    }
                }
            } catch (e: IOException) {
                return@thread
            }
        }
    }

    private fun record(connectivity: ConnectivityManager, buffer: ByteArray, length: Int) {
        val flow = Packets.parse(buffer, length) ?: return
        if (flow.dnsName == null && flow.destination.hostAddress in TUNNEL_DNS_ADDRESSES) return
        val uid = runCatching {
            connectivity.getConnectionOwnerUid(
                flow.protocol,
                InetSocketAddress(flow.source, flow.sourcePort),
                InetSocketAddress(flow.destination, flow.destinationPort),
            )
        }.getOrDefault(UNKNOWN_UID)
        val destination = flow.dnsName ?: flow.destination.hostAddress.orEmpty()
        ActivityLog.record(uid, destination, flow.destinationPort, flow.protocol, System.currentTimeMillis())
    }

    private fun shutDown() {
        closeTunnel()
        running = false
        stopSelf()
    }

    private fun closeTunnel() {
        runCatching { tunnel?.close() }
        tunnel = null
    }

    companion object {
        private const val ACTION_STOP = "dev.nekuz.appwall.action.STOP"
        private const val MTU = 1500
        private const val TUNNEL_ADDRESS_V4 = "10.111.222.2"
        private const val TUNNEL_ADDRESS_V6 = "fd00:6177::2"
        private const val TUNNEL_DNS_V4 = "10.111.222.1"
        private const val TUNNEL_DNS_V6 = "fd00:6177::1"
        const val UNKNOWN_UID = -1
        private val TUNNEL_DNS_ADDRESSES = setOf(
            InetAddress.getByName(TUNNEL_DNS_V4).hostAddress,
            InetAddress.getByName(TUNNEL_DNS_V6).hostAddress,
        )

        @Volatile
        var running = false
            private set

        fun start(context: Context) {
            context.startService(Intent(context, FirewallService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, FirewallService::class.java).setAction(ACTION_STOP))
        }
    }
}
