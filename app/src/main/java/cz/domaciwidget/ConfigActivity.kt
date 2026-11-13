package cz.domaciwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.text.Collator
import java.util.Locale

class ConfigActivity : Activity() {

    // type: 0 = nadpis místnosti, 1 = zařízení, 2 = "bez druhého senzoru"
    private class Row(val type: Int, val text: String, val device: Device?)

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    // 0 = nejdřív hlavní a pak druhý senzor, 1 = jen hlavní, 2 = jen druhý
    private var slot = 0
    private var step = 1
    private var showAll = false
    private var devices: List<Device> = emptyList()
    private var rows: List<Row> = emptyList()
    private lateinit var list: ListView
    private lateinit var info: TextView
    private lateinit var toggle: Button

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun isSensor(d: Device): Boolean =
        d.model.contains("sensor_ht", ignoreCase = true) ||
                d.model.contains("lywsd", ignoreCase = true) ||
                d.specType.contains("temperature-humidity", ignoreCase = true)

    private inner class RowAdapter(private val items: List<Row>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun isEnabled(position: Int): Boolean = items[position].type != 0

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val r = items[position]
            val tv = TextView(this@ConfigActivity)
            tv.text = r.text
            if (r.type == 0) {
                tv.textSize = 13f
                tv.setTypeface(null, Typeface.BOLD)
                tv.setTextColor(Color.parseColor("#E65100"))
                tv.setPadding(dp(4), dp(18), dp(4), dp(6))
            } else {
                tv.textSize = 17f
                tv.setPadding(dp(12), dp(14), dp(12), dp(14))
            }
            return tv
        }
    }

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
        info.textSize = 18f
        info.setPadding(0, 0, 0, dp(4))
        root.addView(info)

        toggle = Button(this)
        toggle.text = "Zobrazit všechna zařízení"
        toggle.setOnClickListener {
            showAll = !showAll
            toggle.text = if (showAll) "Zobrazit jen teploměry" else "Zobrazit všechna zařízení"
            showList()
        }
        root.addView(toggle)

        list = ListView(this)
        root.addView(list)
        setContentView(root)

        if (!Prefs.hasCredentials(this)) {
            info.text = "Nejdřív se přihlaste v aplikaci Domácí widget."
            toggle.visibility = View.GONE
            val b = Button(this)
            b.text = "Otevřít aplikaci"
            b.setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            root.addView(b)
            return
        }

        list.setOnItemClickListener { _, _, pos, _ ->
            val r = rows[pos]
            if (r.type == 1) onPicked(r.device) else if (r.type == 2) onPicked(null)
        }

        devices = Prefs.readDevices(this)
        showList()
        if (devices.isEmpty()) info.text = "Načítám zařízení…"

        Thread {
            try {
                val d = SensorService(this).listDevices()
                Prefs.saveDevices(this, d)
                runOnUiThread {
                    devices = d
                    showList()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (devices.isEmpty()) info.text = "Chyba: " + (e.message ?: e.javaClass.simpleName)
                }
            }
        }.start()
    }

    private fun buildRows(): List<Row> {
        val out = ArrayList<Row>()
        if (step == 2) out.add(Row(2, "— Bez druhého senzoru —", null))

        val shownDevices: List<Device> =
            if (showAll) devices else devices.filter { isSensor(it) }.ifEmpty { devices }

        val grouped = shownDevices.groupBy { it.room }
        val collator = Collator.getInstance(Locale("cs", "CZ"))
        val roomNames = grouped.keys.filter { it.isNotEmpty() }.sortedWith { a, b -> collator.compare(a, b) }

        for (room in roomNames) {
            out.add(Row(0, room, null))
            for (d in grouped[room].orEmpty().sortedWith { a, b -> collator.compare(a.name, b.name) }) {
                out.add(Row(1, d.name, d))
            }
        }
        val noRoom = grouped[""].orEmpty()
        if (noRoom.isNotEmpty()) {
            out.add(Row(0, "Bez místnosti", null))
            for (d in noRoom.sortedWith { a, b -> collator.compare(a.name, b.name) }) {
                out.add(Row(1, d.name, d))
            }
        }
        return out
    }

    private fun showList() {
        rows = buildRows()
        list.adapter = RowAdapter(rows)
        if (devices.isNotEmpty()) {
            info.text = if (step == 1) "Vyberte hlavní senzor:" else "Vyberte druhý senzor:"
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
