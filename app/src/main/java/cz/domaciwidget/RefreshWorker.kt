package cz.domaciwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class RefreshWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, WidgetProvider::class.java))
        if (ids.isEmpty()) return Result.success()

        val dids = ids
            .flatMap { listOfNotNull(Prefs.widgetDid(ctx, it), Prefs.widgetDid2(ctx, it)) }
            .distinct()
        if (dids.isNotEmpty() && Prefs.hasCredentials(ctx)) {
            val svc = SensorService(ctx)
            val errors = ArrayList<String>()
            val now = SimpleDateFormat("d.M. HH:mm", Locale.getDefault()).format(Date())
            for (did in dids) {
                try {
                    val r = svc.fetchReading(did)
                    Prefs.saveReading(ctx, did, r)
                } catch (e: Exception) {
                    errors.add(e.message ?: e.javaClass.simpleName)
                }
            }
            Prefs.setStatus(
                ctx,
                if (errors.isEmpty()) "OK ($now)" else "Chyba ($now): " + errors.joinToString("; ")
            )
        }
        WidgetRenderer.renderAll(ctx)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "periodic_refresh"
        private const val NOW = "refresh_now"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequest.Builder(RefreshWorker::class.java, 15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun enqueueNow(ctx: Context) {
            val req = OneTimeWorkRequest.Builder(RefreshWorker::class.java).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
        }
    }
}
