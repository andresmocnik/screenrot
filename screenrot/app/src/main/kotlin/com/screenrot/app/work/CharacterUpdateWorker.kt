package com.screenrot.app.work

import android.content.Context
import androidx.work.*
import com.screenrot.app.CharacterUpdatePipeline
import java.util.concurrent.TimeUnit

/**
 * WHY WorkManager AND NOT AlarmManager:
 * We don't need exact-time execution (the product brief explicitly says real-time isn't
 * needed) — we need "run roughly every 15 minutes, respecting Doze/App Standby, and let the
 * OS batch it with other apps' work for battery". That's exactly WorkManager's
 * PeriodicWorkRequest design point. AlarmManager (setExactAndAllowWhileIdle, etc.) is for
 * cases that truly need precise wall-clock timing (alarms, calendar reminders) and fighting
 * Doze for that is exactly the battery cost the product brief says to avoid.
 *
 * WHY 15 MINUTES SPECIFICALLY:
 * That's also the minimum interval Android enforces for PeriodicWorkRequest
 * (WorkRequest.MIN_PERIODIC_INTERVAL_MILLIS = 15 min) — so "update ~every 15 min" is both
 * the product's ideal cadence AND the platform's floor. We don't fight the OS to go faster;
 * actual execution will still drift based on Doze/battery state, which is expected and fine
 * since the wallpaper also refreshes on its own visibility/open-app triggers.
 */
object CharacterUpdateWorker {

    private const val UNIQUE_WORK_NAME = "screenrot_periodic_update"

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            return try {
                CharacterUpdatePipeline(applicationContext).refresh()
                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            // Battery is the product's explicit priority #2 — don't run on critically low battery.
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<Worker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
