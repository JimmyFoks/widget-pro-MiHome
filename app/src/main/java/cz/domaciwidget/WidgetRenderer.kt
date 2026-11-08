package cz.domaciwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
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
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = am.nextAlarmClock
        val alarmText: String
        if (next == null) {
            alarmText = "⏰ žádný budík"
        } else {
            val t = Date(next.triggerTime)
            val soon = next.triggerTime - System.currentTimeMillis() < 20L * 3600L * 1000L
            alarmText = if (soon) {
                "⏰ " + timeFmt.format(t)
            } else {
                "⏰ " + SimpleDateFormat("EEE", Locale.getDefault()).format(t) + " " + timeFmt.format(t)
            }
        }
        v.setTextViewText(R.id.alarm, alarmText)

        // --- hlavní senzor ---
        val did = Prefs.widgetDid(ctx, id)
        if (did == null) {
            v.setTextViewText(R.id.sensor_name, "Klepněte a vyberte senzor")
            v.setTextViewText(R.id.temp, "-- °C")
            v.setTextViewText(R.id.hum_bat, "")
        } else {
            val name = Prefs.widgetName(ctx, id) ?: "Senzor"
            val r = Prefs.reading(ctx, did)
            if (r == null) {
                v.setTextViewText(R.id.sensor_name, name)
                v.setTextViewText(R.id.temp, "-- °C")
                v.setTextViewText(R.id.hum_bat, "čekám na data…")
            } else {
                val stale = System.currentTimeMillis() - r.ts > 90L * 60L * 1000L
                val head = name + " · " + timeFmt.format(Date(r.ts)) + (if (stale) " ⚠" else "")
                v.setTextViewText(R.id.sensor_name, head)
                v.setTextViewText(R.id.temp, fmtTemp(r.temp))
                val hum = r.hum
                val bat = r.bat
                val parts = ArrayList<String>()
                if (hum != null) parts.add("💧 " + Math.round(hum) + " %")
                if (bat != null) parts.add("🔋 " + bat + " %")
                v.setTextViewText(R.id.hum_bat, parts.joinToString("  "))
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
                v.setTextViewText(R.id.sensor2_val, "-- °C")
            } else {
                val h2 = r2.hum
                val hText = if (h2 != null) "  💧 " + Math.round(h2) + " %" else ""
                v.setTextViewText(R.id.sensor2_val, fmtTemp(r2.temp) + hText)
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
        v.setOnClickPendingIntent(R.id.hum_bat, refresh)

        val alarms = PendingIntent.getActivity(
            ctx, 1,
            Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            flags
        )
        v.setOnClickPendingIntent(R.id.clock, alarms)
        v.setOnClickPendingIntent(R.id.date, alarms)
        v.setOnClickPendingIntent(R.id.alarm, alarms)

        v.setOnClickPendingIntent(R.id.sensor_name, configIntent(ctx, id, 1, flags))
        val cfg2 = configIntent(ctx, id, 2, flags)
        v.setOnClickPendingIntent(R.id.row2, cfg2)
        v.setOnClickPendingIntent(R.id.sensor2_name, cfg2)
        v.setOnClickPendingIntent(R.id.sensor2_val, cfg2)

        return v
    }
}
