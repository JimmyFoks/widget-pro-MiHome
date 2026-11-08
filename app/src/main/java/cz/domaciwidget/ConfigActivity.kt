package cz.domaciwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

class ConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    // 0 = nejdřív hlavní a pak druhý senzor, 1 = jen hlavní, 2 = jen druhý
    private var slot = 0
    private var step = 1
    private var devices: List<Device> = emptyList()
    private var shown: List<Device?> = emptyList()
    private lateinit var list: ListView
    private lateinit var info: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun isSensor(d: Device): Boolean =
        d.model.contains("sensor_ht", ignoreCase = true) ||
                d.model.contains("lywsd", ignoreCase = true) ||
                d.specType.contains("temperature-humidity", ignoreCase = true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        slot = intent?.getIntExtra(EXTRA_SLOT, 0) ?: 0
        step = if (slot == 2) 2 else 1

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(16))

        info = TextView(this)
        info.textSize = 16f
        info.setPadding(0, 0, 0, dp(8))
        root.addView(info)

        list = ListView(this)
        root.addView(list)
        setContentView(root)

        if (!Prefs.hasCredentials(this)) {
            info.text = "Nejdřív se přihlaste v aplikaci Domácí widget."
            val b = Button(this)
            b.text = "Otevřít aplikaci"
            b.setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            root.addView(b)
            return
        }

        list.setOnItemClickListener { _, _, pos, _ -> onPicked(shown[pos]) }

        devices = sortDevices(Prefs.readDevices(this))
        showList()
        if (devices.isEmpty()) info.text = "Načítám zařízení…"

        Thread {
            try {
                val d = SensorService(this).listDevices()
                Prefs.saveDevices(this, d)
                runOnUiThread {
                    devices = sortDevices(d)
                    showList()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (devices.isEmpty()) info.text = "Chyba: " + (e.message ?: e.javaClass.simpleName)
                }
            }
        }.start()
    }

    private fun sortDevices(d: List<Device>): List<Device> =
        d.sortedWith(compareByDescending<Device> { isSensor(it) }.thenBy { it.name })

    private fun showList() {
        val items = ArrayList<Device?>()
        if (step == 2) items.add(null)
        items.addAll(devices)
        shown = items
        list.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            items.map { if (it == null) "— Bez druhého senzoru —" else it.name + "  ·  " + it.model }
        )
        if (devices.isNotEmpty()) {
            info.text = if (step == 1) "Vyberte hlavní senzor (větší zobrazení):"
            else "Vyberte druhý senzor (menší zobrazení):"
        }
    }

    private fun finishOk() {
        RefreshWorker.schedule(this)
        RefreshWorker.enqueueNow(this)
        WidgetRenderer.renderAll(this)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_OK, result)
    }

    private fun onPicked(d: Device?) {
        if (step == 1) {
            if (d == null) return
            Prefs.setWidget(this, widgetId, d.did, d.name)
            finishOk()
            if (slot == 1) {
                finish()
            } else {
                step = 2
                showList()
            }
        } else {
            if (d == null) {
                Prefs.clearWidget2(this, widgetId)
            } else {
                Prefs.setWidget2(this, widgetId, d.did, d.name)
            }
            finishOk()
            finish()
        }
    }

    companion object {
        const val EXTRA_SLOT = "slot"
    }
}
