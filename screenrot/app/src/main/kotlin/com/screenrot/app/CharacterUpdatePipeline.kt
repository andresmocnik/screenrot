package com.screenrot.app

import android.content.Context
import com.screenrot.app.data.CharacterStateStore
import com.screenrot.app.screentime.UsageStatsRepository
import com.screenrot.core.AppProfileRegistry
import com.screenrot.core.CharacterState
import com.screenrot.core.DamageEngine

/**
 * The one place that wires ScreenTimeRepository -> DamageEngine -> CharacterState -> store.
 * Called from: app open (MainViewModel), the periodic WorkManager job, and the wallpaper
 * engine's own periodic tick. Kept tiny and side-effect-explicit so all three callers behave
 * identically instead of each re-implementing "read usage, compute, save".
 */
class CharacterUpdatePipeline(context: Context) {

    private val appContext = context.applicationContext
    private val usageRepo = UsageStatsRepository(appContext)
    private val registry = AppProfileRegistry() // static seed data; cheap to construct per call
    private val store = CharacterStateStore(appContext)

    sealed class Result {
        data class Updated(val state: CharacterState) : Result()
        object PermissionMissing : Result()
    }

    suspend fun refresh(): Result {
        if (!usageRepo.hasUsageAccessPermission()) return Result.PermissionMissing

        val usage = usageRepo.getTodayUsage()
        val state = DamageEngine.compute(usage, registry)
        val topApps = usage.sortedByDescending { it.minutes }.take(3)
            .joinToString(", ") { "${it.displayName} ${it.minutes}m" }
        store.save(state, topApps)
        return Result.Updated(state)
    }
}
