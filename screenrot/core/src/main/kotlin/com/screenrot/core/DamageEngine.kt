package com.screenrot.core

import kotlin.math.exp
import kotlin.math.min

/**
 * Converts a day's app usage into a [CharacterState].
 *
 * CURVE CHOICE
 * ------------
 * damage(x) = 1 - exp(-k * x)
 *
 * This is the standard "saturating exposure" curve (same shape used for radioactive decay,
 * drug absorption, capacitor charging). Reasons it fits this product better than a linear
 * or a hard-threshold curve:
 *
 *  - Bounded: asymptotically approaches 1 but never exceeds it, so no clamping cliffs or
 *    discontinuities are needed at the high end — an 11th hour of TikTok can't "overflow" damage.
 *  - Front-loaded: the derivative is highest at x=0, so the *first* 30-60 minutes already
 *    produce a visible, shareable change ("wait, my hair already looks off?"). That matters
 *    for the product's curiosity/virality goal — waiting hours for the first visible change
 *    would kill the loop. A linear curve gives the same tiny change at minute 30 as at minute 400.
 *  - Diminishing returns at the top: going from 6h to 10h barely changes the character further,
 *    which matches "completamente absurdo" being a ceiling/plateau rather than something that
 *    keeps getting worse forever.
 *
 * k is tuned per channel (see [ChannelTuning]) rather than globally, so hair loss can ramp
 * faster than posture, etc. — a content decision, safe to retune without touching this class.
 *
 * effective minutes per channel = sum over apps of (app.minutes * app.coefficientFor(channel))
 * This is what lets one app hit multiple channels at different rates, and lets unrelated apps
 * (e.g. a messaging app with near-zero hair-loss coefficient) barely move that channel at all.
 */
object DamageEngine {

    /** Per-channel decay-rate constant. Higher = faster to saturate. Tuned against the product
     *  brief's anchor points (30min small change, 2h moderate, 6h heavy, 8h+ near-max). */
    private val CHANNEL_K: Map<DamageChannel, Float> = mapOf(
        DamageChannel.HAIR_LOSS to 0.010f,
        DamageChannel.EYE_FATIGUE to 0.012f,
        DamageChannel.SKIN_FATIGUE to 0.008f,
        DamageChannel.POSTURE to 0.006f,
        DamageChannel.WEIGHT_CHANGE to 0.004f,
        DamageChannel.EAR_DISTORTION to 0.003f,
        DamageChannel.NOSE_DISTORTION to 0.003f,
        DamageChannel.MOUTH_DISTORTION to 0.003f
    )

    /** k for the headline overallDamage %, computed from *raw* total minutes (unweighted by
     *  any single channel) so it reads as "how much phone today", independent of which apps. */
    private const val OVERALL_K = 0.006f

    /** Soft ceiling so the curve reads as "plateaus" rather than "hits exactly 100%". */
    private const val MAX_CHANNEL_DAMAGE = 0.97f

    fun compute(usage: List<AppUsage>, registry: AppProfileRegistry): CharacterState {
        if (usage.isEmpty()) return CharacterState.PRISTINE

        val effectiveMinutes = mutableMapOf<DamageChannel, Float>()
        var totalMinutes = 0

        for (entry in usage) {
            if (entry.minutes <= 0) continue
            totalMinutes += entry.minutes
            val profile = registry.resolve(entry.packageName, entry.displayName)
            for (channel in DamageChannel.values()) {
                val coeff = profile.coefficientFor(channel)
                if (coeff <= 0f) continue
                effectiveMinutes[channel] = (effectiveMinutes[channel] ?: 0f) + entry.minutes * coeff
            }
        }

        fun saturate(minutes: Float, k: Float): Float =
            min(MAX_CHANNEL_DAMAGE, (1f - exp((-k * minutes).toDouble())).toFloat())

        val hairLoss = saturate(effectiveMinutes[DamageChannel.HAIR_LOSS] ?: 0f, CHANNEL_K.getValue(DamageChannel.HAIR_LOSS))
        val eyeFatigue = saturate(effectiveMinutes[DamageChannel.EYE_FATIGUE] ?: 0f, CHANNEL_K.getValue(DamageChannel.EYE_FATIGUE))
        val skinFatigue = saturate(effectiveMinutes[DamageChannel.SKIN_FATIGUE] ?: 0f, CHANNEL_K.getValue(DamageChannel.SKIN_FATIGUE))
        val posture = saturate(effectiveMinutes[DamageChannel.POSTURE] ?: 0f, CHANNEL_K.getValue(DamageChannel.POSTURE))
        val weightChange = saturate(effectiveMinutes[DamageChannel.WEIGHT_CHANGE] ?: 0f, CHANNEL_K.getValue(DamageChannel.WEIGHT_CHANGE))
        val earDistortion = saturate(effectiveMinutes[DamageChannel.EAR_DISTORTION] ?: 0f, CHANNEL_K.getValue(DamageChannel.EAR_DISTORTION))
        val noseDistortion = saturate(effectiveMinutes[DamageChannel.NOSE_DISTORTION] ?: 0f, CHANNEL_K.getValue(DamageChannel.NOSE_DISTORTION))
        val mouthDistortion = saturate(effectiveMinutes[DamageChannel.MOUTH_DISTORTION] ?: 0f, CHANNEL_K.getValue(DamageChannel.MOUTH_DISTORTION))

        val overallDamage = min(MAX_CHANNEL_DAMAGE, (1f - exp((-OVERALL_K * totalMinutes).toDouble())).toFloat())

        return CharacterState(
            hairLoss = hairLoss,
            eyeFatigue = eyeFatigue,
            skinFatigue = skinFatigue,
            posture = posture,
            weightChange = weightChange,
            earDistortion = earDistortion,
            noseDistortion = noseDistortion,
            mouthDistortion = mouthDistortion,
            overallDamage = overallDamage,
            totalMinutes = totalMinutes
        )
    }
}
