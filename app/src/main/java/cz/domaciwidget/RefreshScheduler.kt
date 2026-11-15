package cz.domaciwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Druhý, na WorkManageru nezávislý mechanismus: budík, který se po každém spuštění sám
 * znovu nastaví (i v úsporném režimu).
 */
object RefreshScheduler {

    private const val INTERVAL_MS = 15L * 60L * 1000L

    private fun tickIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, WidgetProvider::class.java).setAction(WidgetProvider.ACTION_TICK)
        return PendingIntent.getBroadcast(
            ctx, 7, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun arm(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + INTERVAL_MS,
            tickIntent(ctx)
        )
    }

    fun disarm(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(tickIntent(ctx))
    }
}
