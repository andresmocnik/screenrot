package com.screenrot.core

/**
 * One damage "channel" the character can suffer on. Adding a new channel means:
 *   1. add it here
 *   2. add a field to [CharacterState]
 *   3. map it to a visual layer in the renderer
 * Nothing else in the engine needs to change.
 */
enum class DamageChannel {
    HAIR_LOSS,
    EYE_FATIGUE,
    SKIN_FATIGUE,
    POSTURE,
    WEIGHT_CHANGE,
    EAR_DISTORTION,
    NOSE_DISTORTION,
    MOUTH_DISTORTION
}

/**
 * Per-app (or per-category) damage coefficients. A coefficient is the *rate* at which that
 * channel saturates for this app, not a final 0-1 value — see [DamageEngine] for the curve.
 * Coefficients are informal weights (roughly 0..1, most apps living in 0.1-0.9) tuned by feel,
 * not measured. They're a content decision, not a technical one — safe to rebalance anytime
 * without touching the engine.
 */
data class AppProfile(
    val packageName: String,
    val displayName: String,
    val category: AppCategory,
    val coefficients: Map<DamageChannel, Float>
) {
    fun coefficientFor(channel: DamageChannel): Float = coefficients[channel] ?: 0f
}

enum class AppCategory {
    SHORT_VIDEO,
    SOCIAL,
    VIDEO,
    MESSAGING,
    BROWSER,
    GAME,
    PRODUCTIVITY,
    UNKNOWN
}

/**
 * Default coefficients per category, used when an installed app has no explicit [AppProfile].
 * This is what keeps the app from breaking / feeling incomplete when the user has something
 * we've never heard of installed — it still does *something* plausible.
 */
object CategoryDefaults {
    private val defaults: Map<AppCategory, Map<DamageChannel, Float>> = mapOf(
        AppCategory.SHORT_VIDEO to mapOf(
            DamageChannel.HAIR_LOSS to 0.7f,
            DamageChannel.EYE_FATIGUE to 0.6f,
            DamageChannel.POSTURE to 0.3f,
            DamageChannel.MOUTH_DISTORTION to 0.2f
        ),
        AppCategory.SOCIAL to mapOf(
            DamageChannel.EYE_FATIGUE to 0.5f,
            DamageChannel.HAIR_LOSS to 0.4f,
            DamageChannel.SKIN_FATIGUE to 0.4f
        ),
        AppCategory.VIDEO to mapOf(
            DamageChannel.EYE_FATIGUE to 0.5f,
            DamageChannel.WEIGHT_CHANGE to 0.4f,
            DamageChannel.POSTURE to 0.6f
        ),
        AppCategory.MESSAGING to mapOf(
            DamageChannel.EYE_FATIGUE to 0.25f,
            DamageChannel.POSTURE to 0.15f
        ),
        AppCategory.BROWSER to mapOf(
            DamageChannel.EYE_FATIGUE to 0.35f,
            DamageChannel.SKIN_FATIGUE to 0.2f
        ),
        AppCategory.GAME to mapOf(
            DamageChannel.EYE_FATIGUE to 0.45f,
            DamageChannel.POSTURE to 0.4f,
            DamageChannel.EAR_DISTORTION to 0.15f
        ),
        AppCategory.PRODUCTIVITY to mapOf(
            DamageChannel.EYE_FATIGUE to 0.2f,
            DamageChannel.POSTURE to 0.3f
        ),
        AppCategory.UNKNOWN to mapOf(
            DamageChannel.EYE_FATIGUE to 0.25f,
            DamageChannel.SKIN_FATIGUE to 0.15f
        )
    )

    fun profileFor(packageName: String, displayName: String, category: AppCategory): AppProfile =
        AppProfile(packageName, displayName, category, defaults.getValue(category))
}

/**
 * In-memory registry of known apps. Seeded with a handful of well-known packages as examples;
 * anything not listed falls back to [CategoryDefaults] by inferred category (or UNKNOWN),
 * so the app never breaks on an app we've never heard of. Extend by calling [register] —
 * e.g. from a remote-config fetch later, without touching the engine.
 */
class AppProfileRegistry {

    private val profiles = mutableMapOf<String, AppProfile>()
    private val categoryHints = mutableMapOf<String, AppCategory>()

    init {
        register(
            AppProfile(
                packageName = "com.zhiliaoapp.musically", // TikTok
                displayName = "TikTok",
                category = AppCategory.SHORT_VIDEO,
                coefficients = mapOf(
                    DamageChannel.HAIR_LOSS to 0.8f,
                    DamageChannel.EYE_FATIGUE to 0.6f,
                    DamageChannel.POSTURE to 0.2f,
                    DamageChannel.EAR_DISTORTION to 0.1f
                )
            )
        )
        register(
            AppProfile(
                packageName = "com.instagram.android",
                displayName = "Instagram",
                category = AppCategory.SOCIAL,
                coefficients = mapOf(
                    DamageChannel.EYE_FATIGUE to 0.6f,
                    DamageChannel.HAIR_LOSS to 0.5f,
                    DamageChannel.SKIN_FATIGUE to 0.5f
                )
            )
        )
        register(
            AppProfile(
                packageName = "com.google.android.youtube",
                displayName = "YouTube",
                category = AppCategory.VIDEO,
                coefficients = mapOf(
                    DamageChannel.EYE_FATIGUE to 0.5f,
                    DamageChannel.WEIGHT_CHANGE to 0.4f,
                    DamageChannel.POSTURE to 0.7f
                )
            )
        )
        register(
            AppProfile(
                packageName = "com.whatsapp",
                displayName = "WhatsApp",
                category = AppCategory.MESSAGING,
                coefficients = mapOf(DamageChannel.EYE_FATIGUE to 0.3f)
            )
        )
        register(
            AppProfile(
                packageName = "com.android.chrome",
                displayName = "Chrome",
                category = AppCategory.BROWSER,
                coefficients = mapOf(
                    DamageChannel.EYE_FATIGUE to 0.35f,
                    DamageChannel.SKIN_FATIGUE to 0.2f
                )
            )
        )
    }

    fun register(profile: AppProfile) {
        profiles[profile.packageName] = profile
    }

    fun registerCategoryHint(packageName: String, category: AppCategory) {
        categoryHints[packageName] = category
    }

    /** Never throws / never returns null — unknown apps always resolve to a usable profile. */
    fun resolve(packageName: String, displayNameFallback: String): AppProfile {
        profiles[packageName]?.let { return it }
        val category = categoryHints[packageName] ?: AppCategory.UNKNOWN
        return CategoryDefaults.profileFor(packageName, displayNameFallback, category)
    }
}
