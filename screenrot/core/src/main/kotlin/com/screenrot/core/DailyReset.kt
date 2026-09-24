package com.screenrot.core

import java.time.LocalDate
import java.time.ZoneId

/**
 * Decides whether stored state belongs to "today" or needs to be thrown away.
 *
 * Deliberately does NOT rely on any alarm/job firing exactly at 00:00 — Android can (and will)
 * delay, batch, or skip background work. Instead every read path compares the stored date
 * against the current local date and resets on mismatch. This makes the reset correct even if:
 *   - the process was killed all night and never woke up at midnight
 *   - the device was off at midnight
 *   - the user changed timezone overnight (we intentionally reset in that case too — "today"
 *     is evaluated in the device's current zone, which is what the user experiences)
 */
object DailyReset {

    /** Returns true if [storedDateEpochDay] (a LocalDate.toEpochDay() value, or null if no
     *  state has ever been stored) no longer matches "today" in [zone]. */
    fun needsReset(storedDateEpochDay: Long?, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (storedDateEpochDay == null) return true
        return storedDateEpochDay != LocalDate.now(zone).toEpochDay()
    }

    fun todayEpochDay(zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.now(zone).toEpochDay()
}
