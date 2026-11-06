package dev.nekuz.appwall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.UserManager
import java.io.FileInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread

class FirewallService : VpnService() {

    @Volatile
    private var tunnel: ParcelFileDescriptor? = null

    private val packageAdded = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val added = intent.data?.schemeSpecificPart ?: return
            if (tunnel != null && added in Rules.blocked(context)) applyRules()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        registerReceiver(packageAdded, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Rules.setEnabled(this, false)
            shutDown()
            return START_NOT_STICKY
        }
        Rules.setEnabled(this, true)
        runCatching { enterForeground() }
        return if (applyRules()) START_STICKY else START_NOT_STICKY
    }

    override fun onRevoke() {
        Rules.setEnabled(this, false)
        shutDown()
        Notices.showOff(this)
        super.onRevoke()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(packageAdded) }
        closeTunnel()
        running = false
        ActivityLog.clear()
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
        val covered = coveredUids(this)
        val next = runCatching { builder.establish() }.getOrNull()
        if (next == null) {
            shutDown()
            Notices.showOff(this)
            return false
        }
        val previous = tunnel
        tunnel = next
        appliedUids = covered
        running = true
        startReader(next)
        runCatching { previous?.close() }
        Notices.clearOff(this)
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
                        if (length > 0 && tunnel === descriptor) {
                            runCatching { record(connectivity, buffer, length) }
                        }
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

    private fun enterForeground() {
        val notification = Notices.ongoing(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notices.ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notices.ONGOING_ID, notification)
        }
    }

    private fun shutDown() {
        closeTunnel()
        running = false
        ActivityLog.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closeTunnel() {
        val current = tunnel
        tunnel = null
        runCatching { current?.close() }
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
        private var appliedUids: Set<Int> = emptySet()

        @Volatile
        var running = false
            private set

        fun start(context: Context): Boolean = runCatching {
            context.startForegroundService(Intent(context, FirewallService::class.java))
        }.isSuccess

        fun reapply(context: Context) {
            if (running && Rules.isEnabled(context) && coveredUids(context) != appliedUids) start(context)
        }

        private fun coveredUids(context: Context): Set<Int> = Rules.blocked(context)
            .mapNotNull { name -> runCatching { context.packageManager.getPackageUid(name, 0) }.getOrNull() }
            .toSet()

        fun stop(context: Context) {
            Rules.setEnabled(context, false)
            runCatching {
                context.startService(Intent(context, FirewallService::class.java).setAction(ACTION_STOP))
            }
        }

        fun otherVpnActive(context: Context): Boolean {
            if (running) return false
            return runCatching {
                val connectivity = context.getSystemService(ConnectivityManager::class.java)
                val profiles = context.getSystemService(UserManager::class.java).userProfiles.size
                @Suppress("DEPRECATION")
                val networks = if (profiles > 1) {
                    listOfNotNull(connectivity.activeNetwork)
                } else {
                    connectivity.allNetworks.toList()
                }
                networks.any {
                    connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                }
            }.getOrDefault(true)
        }
    }
}
