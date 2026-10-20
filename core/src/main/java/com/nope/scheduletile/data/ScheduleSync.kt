package com.nope.scheduletile.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object ScheduleSync {
    private const val BASE = "https://nope0183.github.io/schedule/"
    private const val PERIODIC = "schedule-sync-charging"
    private const val INITIAL = "schedule-sync-initial"

    /** Скачивает schedule.json и history.json, кладёт в кэш и обновляет виджет и комплекции. */
    suspend fun fetchAll(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val cur = download("schedule.json")
        val hist = download("history.json")
        if (cur == null && hist == null) return@withContext false
        cur?.let { ctx.openFileOutput("schedule.json", Context.MODE_PRIVATE).use { o -> o.write(it.toByteArray()) } }
        hist?.let { ctx.openFileOutput("history.json", Context.MODE_PRIVATE).use { o -> o.write(it.toByteArray()) } }
        Prefs.setLastSync(ctx, System.currentTimeMillis())
        Surfaces.refreshAll(ctx)
        true
    }

    private fun download(name: String): String? = try {
        val c = URL("$BASE$name?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 15_000
        if (c.responseCode != 200) {
            null
        } else {
            c.inputStream.bufferedReader().use { it.readText() }.also { JSONObject(it) } // валидация JSON
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Периодическая загрузка. На часах [requiresCharging] = true (только на зарядке),
     * на телефоне false (просто раз в [intervalHours] при наличии сети).
     */
    fun schedulePeriodic(ctx: Context, requiresCharging: Boolean, intervalHours: Long = 1) {
        val constraints = Constraints.Builder()
            .setRequiresCharging(requiresCharging)
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val req = PeriodicWorkRequestBuilder<SyncWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Разовая загрузка прямо сейчас (например, при добавлении виджета). */
    fun enqueueNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("schedule-sync-now", ExistingWorkPolicy.REPLACE, req)
    }

    /** Единственное исключение: при первом запуске, пока кэша нет, один раз качаем сразу. */
    fun fetchOnceIfNoCache(ctx: Context) {
        if (ScheduleRepository.hasCache(ctx)) return
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(INITIAL, ExistingWorkPolicy.KEEP, req)
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result =
        if (ScheduleSync.fetchAll(applicationContext)) Result.success() else Result.retry()
}
