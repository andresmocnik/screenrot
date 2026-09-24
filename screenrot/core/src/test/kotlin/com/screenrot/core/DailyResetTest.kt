package com.screenrot.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DailyResetTest {

    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")

    @Test
    fun `null stored date always needs reset (first run)`() {
        assertTrue(DailyReset.needsReset(null, zone))
    }

    @Test
    fun `same day does not need reset`() {
        val today = LocalDate.now(zone).toEpochDay()
        assertFalse(DailyReset.needsReset(today, zone))
    }

    @Test
    fun `previous day needs reset`() {
        val yesterday = LocalDate.now(zone).minusDays(1).toEpochDay()
        assertTrue(DailyReset.needsReset(yesterday, zone))
    }

    @Test
    fun `future stored date still triggers reset (clock changed) rather than crashing`() {
        val tomorrow = LocalDate.now(zone).plusDays(1).toEpochDay()
        assertTrue(DailyReset.needsReset(tomorrow, zone))
    }
}
