package dev.nekuz.appwall

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView

class ActivityLogScreen : Activity() {

    private lateinit var list: ListView
    private lateinit var empty: TextView

    private var attempts: List<Attempt> = emptyList()
    private val labels = HashMap<Int, String>()
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, REFRESH_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        list.adapter = AttemptAdapter()
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.clearButton).setOnClickListener {
            ActivityLog.clear()
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun render() {
        attempts = ActivityLog.snapshot()
        (list.adapter as BaseAdapter).notifyDataSetChanged()
        empty.visibility = if (attempts.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun labelFor(uid: Int): String = labels.getOrPut(uid) {
        runCatching {
            val packageName = packageManager.getPackagesForUid(uid)?.firstOrNull()
            packageName?.let { packageManager.getApplicationInfo(it, 0).loadLabel(packageManager).toString() }
        }.getOrNull() ?: getString(R.string.log_unknown_app)
    }

    private fun describe(attempt: Attempt): String {
        if (attempt.port == DNS_PORT && !isAddress(attempt.destination)) {
            return getString(R.string.log_dns_lookup, attempt.destination)
        }
        val protocol = if (attempt.protocol == Packets.PROTOCOL_TCP) "TCP" else "UDP"
        val address = if (attempt.destination.contains(':')) "[${attempt.destination}]" else attempt.destination
        return "$address:${attempt.port} ($protocol)"
    }

    private fun isAddress(destination: String) =
        destination.contains(':') || destination.all { it.isDigit() || it == '.' }

    private inner class AttemptAdapter : BaseAdapter() {
        override fun getCount() = attempts.size
        override fun getItem(position: Int) = attempts[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.row_log, parent, false)
            val attempt = attempts[position]
            view.findViewById<TextView>(R.id.app).text = labelFor(attempt.uid)
            view.findViewById<TextView>(R.id.destination).text = describe(attempt)
            view.findViewById<TextView>(R.id.detail).text = getString(
                R.string.log_attempts,
                attempt.count,
                DateUtils.getRelativeTimeSpanString(attempt.lastSeenMillis),
            )
            return view
        }
    }

    private companion object {
        const val REFRESH_MILLIS = 2000L
        const val DNS_PORT = 53
    }
}
