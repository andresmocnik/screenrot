package com.screenrot.core

/**
 * Snapshot of how "damaged" the character is right now. Every field is clamped to [0, 1].
 * This is the *only* thing the renderer needs to know about — it has no idea what apps
 * were used, for how long, or what day it is.
 */
data class CharacterState(
    val hairLoss: Float = 0f,
    val eyeFatigue: Float = 0f,
    val skinFatigue: Float = 0f,
    val posture: Float = 0f,
    val weightChange: Float = 0f,
    val earDistortion: Float = 0f,
    val noseDistortion: Float = 0f,
    val mouthDistortion: Float = 0f,
    /** Aggregate 0-1 "how wrecked is this character" used for the headline % shown to the user. */
    val overallDamage: Float = 0f,
    /** Total minutes across all apps that fed this state — shown in the UI, not used by the renderer. */
    val totalMinutes: Int = 0
) {
    companion object {
        val PRISTINE = CharacterState()
    }

    fun channel(c: DamageChannel): Float = when (c) {
        DamageChannel.HAIR_LOSS -> hairLoss
        DamageChannel.EYE_FATIGUE -> eyeFatigue
        DamageChannel.SKIN_FATIGUE -> skinFatigue
        DamageChannel.POSTURE -> posture
        DamageChannel.WEIGHT_CHANGE -> weightChange
        DamageChannel.EAR_DISTORTION -> earDistortion
        DamageChannel.NOSE_DISTORTION -> noseDistortion
        DamageChannel.MOUTH_DISTORTION -> mouthDistortion
    }
}

/** Minutes used for a single app today — the raw input the engine consumes. */
data class AppUsage(
    val packageName: String,
    val displayName: String,
    val minutes: Int
)
