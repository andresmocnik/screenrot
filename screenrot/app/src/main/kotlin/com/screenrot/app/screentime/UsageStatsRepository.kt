package com.screenrot.app.screentime

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.screenrot.core.AppUsage
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads today's per-app foreground usage from [UsageStatsManager].
 *
 * WHY queryUsageStats(INTERVAL_BEST, ...) INSTEAD OF queryEvents:
 * queryUsageStats with an explicit [beginOfToday, now) range and INTERVAL_BEST returns one
 * UsageStats bucket per package already aggregated for us — simplest path to "minutes used
 * today per app", and it's the same primitive used by Android's own Digital Wellbeing.
 * queryEvents(...) (raw MOVE_TO_FOREGROUND/MOVE_TO_BACKGROUND events) would let us do finer
 * session-level analysis later (e.g. distinguishing many short checks from one long binge),
 * but it's unnecessary complexity for the MVP's "minutes per app today" need. The repository
 * is written so swapping the aggregation strategy later doesn't touch any caller.
 *
 * PERMISSION: this requires the special "Usage access" permission (android.permission
 * .PACKAGE_USAGE_STATS), which — unlike a normal dangerous permission — CANNOT be requested
 * via ActivityCompat.requestPermissions. The user must grant it manually in
 * Settings > Apps > Special app access > Usage access. We detect the grant via AppOpsManager
 * (checkOpNoThrow(OPSTR_GET_USAGE_STATS)) rather than PackageManager, which is the documented
 * way to check this particular permission's actual state.
 */
class UsageStatsRepository(private val context: Context) {

    fun hasUsageAccessPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Minutes used today per installed app, filtered to apps with >= 1 minute and excluding
     * our own package. Returns an empty list (never throws) if permission isn't granted —
     * callers should check [hasUsageAccessPermission] first to distinguish "no usage" from
     * "no permission" in the UI.
     */
    fun getTodayUsage(zone: ZoneId = ZoneId.systemDefault()): List<AppUsage> {
        if (!hasUsageAccessPermission()) return emptyList()

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager

        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startOfDay,
            now
        ) ?: return emptyList()

        return stats
            .filter { it.packageName != context.packageName }
            .mapNotNull { s ->
                val minutes = (s.totalTimeInForeground / 1000 / 60).toInt()
                if (minutes < 1) return@mapNotNull null
                val label = try {
                    val appInfo = pm.getApplicationInfo(s.packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    s.packageName
                }
                AppUsage(packageName = s.packageName, displayName = label, minutes = minutes)
            }
            // queryUsageStats can return multiple overlapping buckets for the same package
            // depending on how the system chose to aggregate INTERVAL_BEST; collapse by max.
            .groupBy { it.packageName }
            .map { (_, entries) -> entries.maxByOrNull { it.minutes }!! }
            .sortedByDescending { it.minutes }
    }

    fun totalMinutesToday(zone: ZoneId = ZoneId.systemDefault()): Int =
        getTodayUsage(zone).sumOf { it.minutes }
}
