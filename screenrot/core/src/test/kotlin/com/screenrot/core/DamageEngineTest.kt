package com.screenrot.core

import org.junit.Assert.*
import org.junit.Test

class DamageEngineTest {

    private val registry = AppProfileRegistry()

    private fun usage(pkg: String, minutes: Int) = AppUsage(pkg, pkg, minutes)

    @Test
    fun `no usage means pristine character`() {
        val state = DamageEngine.compute(emptyList(), registry)
        assertEquals(0f, state.overallDamage, 0.001f)
        assertEquals(CharacterState.PRISTINE, state)
    }

    @Test
    fun `30 minutes produces small but nonzero damage`() {
        val state = DamageEngine.compute(listOf(usage("com.zhiliaoapp.musically", 30)), registry)
        assertTrue("expected small damage, got ${state.overallDamage}", state.overallDamage in 0.05f..0.30f)
        assertTrue(state.hairLoss > 0f)
    }

    @Test
    fun `2 hours produces moderate damage`() {
        val state = DamageEngine.compute(listOf(usage("com.zhiliaoapp.musically", 120)), registry)
        assertTrue("expected moderate damage, got ${state.overallDamage}", state.overallDamage in 0.35f..0.70f)
    }

    @Test
    fun `6 hours produces high damage`() {
        val state = DamageEngine.compute(listOf(usage("com.zhiliaoapp.musically", 360)), registry)
        assertTrue("expected high damage, got ${state.overallDamage}", state.overallDamage > 0.80f)
    }

    @Test
    fun `10 hours saturates near the max instead of exceeding 1`() {
        val state = DamageEngine.compute(listOf(usage("com.zhiliaoapp.musically", 600)), registry)
        assertTrue(state.overallDamage <= 0.97f)
        assertTrue(state.overallDamage > 0.90f)
        // every channel must stay within bounds no matter how extreme the input
        assertTrue(state.hairLoss <= 1f && state.eyeFatigue <= 1f && state.posture <= 1f)
    }

    @Test
    fun `damage never grows unbounded with more minutes`() {
        val a = DamageEngine.compute(listOf(usage("com.zhiliaoapp.musically", 600)), registry)
        val b = DamageEngine.compute(listOf(usage("com.zhiliaoapp.musically", 6000)), registry)
        assertTrue(b.overallDamage <= 0.97f)
        assertTrue(b.overallDamage >= a.overallDamage) // monotonic, but both capped
    }

    @Test
    fun `unknown app still produces a plausible result via category default`() {
        val state = DamageEngine.compute(listOf(usage("com.some.random.unknown.app", 90)), registry)
        assertTrue(state.overallDamage > 0f)
        assertEquals(90, state.totalMinutes)
    }

    @Test
    fun `multiple apps accumulate on shared channels`() {
        val single = DamageEngine.compute(listOf(usage("com.instagram.android", 60)), registry)
        val combined = DamageEngine.compute(
            listOf(usage("com.instagram.android", 60), usage("com.google.android.youtube", 60)),
            registry
        )
        assertTrue(combined.eyeFatigue > single.eyeFatigue)
        assertEquals(120, combined.totalMinutes)
    }

    @Test
    fun `zero minute entries are ignored without crashing`() {
        val state = DamageEngine.compute(listOf(usage("com.whatsapp", 0)), registry)
        assertEquals(0, state.totalMinutes)
        assertEquals(0f, state.overallDamage, 0.001f)
    }

    @Test
    fun `messaging app barely affects hair loss compared to short video`() {
        val messaging = DamageEngine.compute(listOf(usage("com.whatsapp", 120)), registry)
        val shortVideo = DamageEngine.compute(listOf(usage("com.zhiliaoapp.musically", 120)), registry)
        assertTrue(messaging.hairLoss < shortVideo.hairLoss)
    }
}
