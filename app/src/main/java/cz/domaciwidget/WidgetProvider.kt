package cz.domaciwidget

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRenderer.renderAll(context)
        RefreshWorker.schedule(context)
        RefreshWorker.enqueueNow(context)
    }

    override fun onEnabled(context: Context) {
        RefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        RefreshWorker.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) Prefs.removeWidget(context, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_REFRESH) {
            RefreshWorker.enqueueNow(context)
            WidgetRenderer.renderAll(context)
        } else if (action == AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) {
            WidgetRenderer.renderAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "cz.domaciwidget.REFRESH"
    }
}
