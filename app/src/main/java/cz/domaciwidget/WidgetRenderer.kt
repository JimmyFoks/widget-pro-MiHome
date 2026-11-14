package cz.domaciwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.util.Date
import java.util.Locale

object WidgetRenderer {

    fun renderAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, WidgetProvider::class.java))
        for (id in ids) {
            mgr.updateAppWidget(id, build(ctx, id))
        }
    }

    private fun fmtTemp(t: Double?): String =
        if (t != null) String.format(Locale.getDefault(), "%.1f °C", t) else "-- °C"

    private fun configIntent(ctx: Context, id: Int, slot: Int, flags: Int): PendingIntent {
        val intent = Intent(ctx, ConfigActivity::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(ConfigActivity.EXTRA_SLOT, slot)
        return PendingIntent.getActivity(ctx, 1000 + id * 4 + slot, intent, flags)
    }

    private fun build(ctx: Context, id: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget)
        val timeFmt = android.text.format.DateFormat.getTimeFormat(ctx)

        // --- nejbližší budík ---
        v.setTextViewText(R.id.alarm, AlarmInfo.nextText(ctx) ?: "žádný budík")

        // --- hlavní senzor ---
        val did = Prefs.widgetDid(ctx, id)
        if (did == null) {
            v.setTextViewText(R.id.sensor_name, "Klepněte a vyberte senzor")
            v.setTextViewText(R.id.temp, "-- °C")
            v.setViewVisibility(R.id.hum, View.GONE)
            v.setViewVisibility(R.id.range, View.GONE)
        } else {
            v.setTextViewText(R.id.sensor_name, Prefs.widgetName(ctx, id) ?: "Senzor")
            val r = Prefs.reading(ctx, did)
            if (r == null) {
                v.setTextViewText(R.id.temp, "-- °C")
                v.setViewVisibility(R.id.hum, View.GONE)
                v.setViewVisibility(R.id.range, View.GONE)
            } else {
                v.setTextViewText(R.id.temp, fmtTemp(r.temp))

                val stale = System.currentTimeMillis() - r.ts > 90L * 60L * 1000L
                val parts = ArrayList<String>()
                val hum = r.hum
                if (hum != null) parts.add("💧 " + Math.round(hum).toString() + " %")
                parts.add(timeFmt.format(Date(r.ts)) + (if (stale) " !" else ""))
                v.setViewVisibility(R.id.hum, View.VISIBLE)
                v.setTextViewText(R.id.hum, parts.joinToString("  ·  "))

                val range = Prefs.dayRange(ctx, did)
                if (range != null) {
                    v.setViewVisibility(R.id.range, View.VISIBLE)
                    v.setTextViewText(
                        R.id.range,
                        String.format(Locale.getDefault(), "↓ %.1f°  ↑ %.1f°", range.first, range.second)
                    )
                } else {
                    v.setViewVisibility(R.id.range, View.GONE)
                }
            }
        }

        // --- druhý (menší) senzor ---
        val did2 = Prefs.widgetDid2(ctx, id)
        if (did2 == null) {
            v.setViewVisibility(R.id.row2, View.GONE)
        } else {
            v.setViewVisibility(R.id.row2, View.VISIBLE)
            v.setTextViewText(R.id.sensor2_name, Prefs.widgetName2(ctx, id) ?: "Senzor 2")
            val r2 = Prefs.reading(ctx, did2)
            if (r2 == null) {
                v.setTextViewText(R.id.sensor2_temp, "-- °C")
                v.setViewVisibility(R.id.sensor2_hum, View.GONE)
            } else {
                v.setTextViewText(R.id.sensor2_temp, fmtTemp(r2.temp))
                val h2 = r2.hum
                if (h2 != null) {
                    v.setViewVisibility(R.id.sensor2_hum, View.VISIBLE)
                    v.setTextViewText(R.id.sensor2_hum, "💧 " + Math.round(h2).toString() + " %")
                } else {
                    v.setViewVisibility(R.id.sensor2_hum, View.GONE)
                }
            }
        }

        // --- klepnutí ---
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val refresh = PendingIntent.getBroadcast(
            ctx, 0,
            Intent(ctx, WidgetProvider::class.java).setAction(WidgetProvider.ACTION_REFRESH),
            flags
        )
        v.setOnClickPendingIntent(R.id.root, refresh)
        v.setOnClickPendingIntent(R.id.temp, refresh)
        v.setOnClickPendingIntent(R.id.hum, refresh)
        v.setOnClickPendingIntent(R.id.range, refresh)

        val clock = PendingIntent.getActivity(ctx, 2, Intent(ctx, ClockActivity::class.java), flags)
        v.setOnClickPendingIntent(R.id.click_left, clock)
        v.setOnClickPendingIntent(R.id.left, clock)
        v.setOnClickPendingIntent(R.id.clock, clock)
        v.setOnClickPendingIntent(R.id.date, clock)
        v.setOnClickPendingIntent(R.id.alarm_row, clock)

        v.setOnClickPendingIntent(R.id.sensor_name, configIntent(ctx, id, 1, flags))
        val cfg2 = configIntent(ctx, id, 2, flags)
        v.setOnClickPendingIntent(R.id.row2, cfg2)
        v.setOnClickPendingIntent(R.id.sensor2_name, cfg2)
        v.setOnClickPendingIntent(R.id.sensor2_temp, cfg2)

        return v
    }
}
