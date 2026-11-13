package cz.domaciwidget

import android.app.AlarmManager
import android.content.Context
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AlarmInfo {

    private const val MIUI_KEY = "next_alarm_clock_formatted"

    private fun setting(ctx: Context, key: String): String? =
        try {
            Settings.System.getString(ctx.contentResolver, key)
        } catch (e: Exception) {
            null
        }

    /** Text nejbližšího budíku, nebo null, když žádný není. */
    fun nextText(ctx: Context): String? {
        // 1) hodnota, kterou si vede aplikace Hodiny od Xiaomi (pokud ji telefon má)
        val miui = setting(ctx, MIUI_KEY)
        if (miui != null) {
            val t = miui.trim()
            return if (t.isEmpty()) null else t
        }

        // 2) standardní nejbližší budík ze systému
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = am.nextAlarmClock ?: return null
        val t = Date(next.triggerTime)
        val timeFmt = android.text.format.DateFormat.getTimeFormat(ctx)
        val soon = next.triggerTime - System.currentTimeMillis() < 20L * 3600L * 1000L
        return if (soon) {
            timeFmt.format(t)
        } else {
            SimpleDateFormat("EEE", Locale.getDefault()).format(t) + " " + timeFmt.format(t)
        }
    }

    /** Diagnostika: co o budíku hlásí systém. */
    fun describe(ctx: Context): String {
        val sb = StringBuilder()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = am.nextAlarmClock
        if (next == null) {
            sb.append("AlarmManager: žádný budík\n")
        } else {
            val f = SimpleDateFormat("EEE d.M. HH:mm", Locale.getDefault()).format(Date(next.triggerTime))
            val pkg = next.showIntent?.creatorPackage ?: "?"
            sb.append("AlarmManager: ").append(f).append(" (aplikace: ").append(pkg).append(")\n")
        }
        for (k in listOf(MIUI_KEY, "next_alarm_formatted")) {
            sb.append(k).append(": ").append(setting(ctx, k) ?: "(neexistuje)").append("\n")
        }
        return sb.toString().trim()
    }
}
