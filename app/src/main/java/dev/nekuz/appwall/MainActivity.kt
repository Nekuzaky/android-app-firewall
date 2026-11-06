package dev.nekuz.appwall

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.VpnService
import android.os.Bundle
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private class App(val label: String, val packageName: String, val icon: Drawable)

    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var toggleButton: Button
    private lateinit var search: EditText
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private lateinit var progress: ProgressBar

    private var allApps: List<App> = emptyList()
    private var visibleApps: List<App> = emptyList()
    private var blocked: Set<String> = emptySet()
    private var loaded = false
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.heroCard).clipToOutline = true
        statusTitle = findViewById(R.id.statusTitle)
        statusSubtitle = findViewById(R.id.statusSubtitle)
        toggleButton = findViewById(R.id.toggleButton)
        search = findViewById(R.id.search)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        progress = findViewById(R.id.progress)

        list.adapter = AppAdapter()
        list.setOnItemClickListener { _, _, position, _ -> toggleApp(visibleApps[position]) }
        toggleButton.setOnClickListener { toggleFirewall() }
        findViewById<View>(R.id.activityButton).setOnClickListener {
            startActivity(Intent(this, ActivityLogScreen::class.java))
        }
        findViewById<View>(R.id.infoButton).setOnClickListener { showInfo() }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })
    }

    override fun onResume() {
        super.onResume()
        blocked = Rules.blocked(this)
        if (Rules.isEnabled(this) && !FirewallService.running && VpnService.prepare(this) == null) {
            startFirewall()
        }
        renderStatus()
        load()
    }

    private fun load() {
        val current = ++generation
        if (!loaded) progress.visibility = View.VISIBLE
        thread {
            val apps = queryApps()
            runOnUiThread {
                if (isDestroyed || current != generation) return@runOnUiThread
                allApps = apps
                val firstLoad = !loaded
                loaded = true
                progress.visibility = View.GONE
                applyFilter()
                if (firstLoad) list.scheduleLayoutAnimation()
            }
        }
    }

    private fun queryApps(): List<App> {
        val launcher = getSystemService(LauncherApps::class.java)
        return launcher.getActivityList(null, Process.myUserHandle())
            .asSequence()
            .map { it.applicationInfo }
            .filter { it.packageName != packageName }
            .distinctBy { it.packageName }
            .filter {
                packageManager.checkPermission(Manifest.permission.INTERNET, it.packageName) ==
                    PackageManager.PERMISSION_GRANTED
            }
            .map { App(it.loadLabel(packageManager).toString(), it.packageName, it.loadIcon(packageManager)) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun applyFilter() {
        val query = search.text.toString().trim()
        visibleApps = allApps
            .filter {
                query.isEmpty() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
            .sortedByDescending { it.packageName in blocked }
        (list.adapter as BaseAdapter).notifyDataSetChanged()
        empty.visibility = if (loaded && visibleApps.isEmpty()) View.VISIBLE else View.GONE
        empty.setText(if (query.isEmpty()) R.string.empty_apps else R.string.empty_search)
    }

    private fun renderStatus() {
        val on = FirewallService.running
        statusTitle.setText(if (on) R.string.status_on else R.string.status_off)
        statusSubtitle.text = resources.getQuantityString(R.plurals.blocked_count, blocked.size, blocked.size)
        toggleButton.setText(if (on) R.string.action_turn_off else R.string.action_turn_on)
    }

    private fun toggleApp(app: App) {
        val nowBlocked = app.packageName !in blocked
        Rules.setBlocked(this, app.packageName, nowBlocked)
        blocked = Rules.blocked(this)
        if (FirewallService.running) FirewallService.start(this)
        renderStatus()
        (list.adapter as BaseAdapter).notifyDataSetChanged()
    }

    private fun toggleFirewall() {
        if (FirewallService.running) {
            FirewallService.stop(this)
            toggleButton.postDelayed({ renderStatus() }, STATUS_REFRESH_MILLIS)
            return
        }
        val consent = VpnService.prepare(this)
        if (consent == null) {
            startFirewall()
        } else {
            @Suppress("DEPRECATION")
            startActivityForResult(consent, REQUEST_VPN)
        }
    }

    private fun startFirewall() {
        FirewallService.start(this)
        toggleButton.postDelayed({ renderStatus() }, STATUS_REFRESH_MILLIS)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_VPN) return
        if (resultCode == RESULT_OK) {
            startFirewall()
        } else {
            Toast.makeText(this, R.string.start_refused, Toast.LENGTH_LONG).show()
        }
    }

    private fun showInfo() {
        AlertDialog.Builder(this)
            .setTitle(R.string.info)
            .setMessage(R.string.info_message)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = visibleApps.size
        override fun getItem(position: Int) = visibleApps[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.row_app, parent, false)
            val app = visibleApps[position]
            view.findViewById<ImageView>(R.id.icon).setImageDrawable(app.icon)
            view.findViewById<TextView>(R.id.label).text = app.label
            view.findViewById<TextView>(R.id.packageName).text = app.packageName
            view.findViewById<Switch>(R.id.blockSwitch).isChecked = app.packageName in blocked
            return view
        }
    }

    private companion object {
        const val REQUEST_VPN = 1
        const val STATUS_REFRESH_MILLIS = 400L
    }
}
