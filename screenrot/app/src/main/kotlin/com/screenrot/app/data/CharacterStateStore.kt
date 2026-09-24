package com.screenrot.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.screenrot.core.CharacterState
import com.screenrot.core.DailyReset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "screenrot_state")

/**
 * Persists ONLY what's needed to redraw today's character: the last computed [CharacterState]
 * and the date it was computed for. Deliberately does not persist raw per-app usage minutes or
 * any history — the product brief calls for local-first, day-scoped state, not a usage log.
 * DataStore (not Room) because this is a single small blob, not relational data.
 */
class CharacterStateStore(private val context: Context) {

    private object Keys {
        val STORED_DATE_EPOCH_DAY = longPreferencesKey("stored_date_epoch_day")
        val HAIR_LOSS = floatPreferencesKey("hair_loss")
        val EYE_FATIGUE = floatPreferencesKey("eye_fatigue")
        val SKIN_FATIGUE = floatPreferencesKey("skin_fatigue")
        val POSTURE = floatPreferencesKey("posture")
        val WEIGHT_CHANGE = floatPreferencesKey("weight_change")
        val EAR_DISTORTION = floatPreferencesKey("ear_distortion")
        val NOSE_DISTORTION = floatPreferencesKey("nose_distortion")
        val MOUTH_DISTORTION = floatPreferencesKey("mouth_distortion")
        val OVERALL_DAMAGE = floatPreferencesKey("overall_damage")
        val TOTAL_MINUTES = intPreferencesKey("total_minutes")
        val TOP_APPS_SUMMARY = stringPreferencesKey("top_apps_summary") // for sharing/UI only
    }

    val state: Flow<CharacterState> = context.dataStore.data.map { prefs ->
        val storedDay = prefs[Keys.STORED_DATE_EPOCH_DAY]
        if (DailyReset.needsReset(storedDay)) {
            CharacterState.PRISTINE
        } else {
            CharacterState(
                hairLoss = prefs[Keys.HAIR_LOSS] ?: 0f,
                eyeFatigue = prefs[Keys.EYE_FATIGUE] ?: 0f,
                skinFatigue = prefs[Keys.SKIN_FATIGUE] ?: 0f,
                posture = prefs[Keys.POSTURE] ?: 0f,
                weightChange = prefs[Keys.WEIGHT_CHANGE] ?: 0f,
                earDistortion = prefs[Keys.EAR_DISTORTION] ?: 0f,
                noseDistortion = prefs[Keys.NOSE_DISTORTION] ?: 0f,
                mouthDistortion = prefs[Keys.MOUTH_DISTORTION] ?: 0f,
                overallDamage = prefs[Keys.OVERALL_DAMAGE] ?: 0f,
                totalMinutes = prefs[Keys.TOTAL_MINUTES] ?: 0
            )
        }
    }

    val topAppsSummary: Flow<String> = context.dataStore.data.map { it[Keys.TOP_APPS_SUMMARY] ?: "" }

    suspend fun currentOrPristine(): CharacterState = state.first()

    suspend fun save(newState: CharacterState, topAppsSummary: String = "") {
        context.dataStore.edit { prefs ->
            prefs[Keys.STORED_DATE_EPOCH_DAY] = DailyReset.todayEpochDay()
            prefs[Keys.HAIR_LOSS] = newState.hairLoss
            prefs[Keys.EYE_FATIGUE] = newState.eyeFatigue
            prefs[Keys.SKIN_FATIGUE] = newState.skinFatigue
            prefs[Keys.POSTURE] = newState.posture
            prefs[Keys.WEIGHT_CHANGE] = newState.weightChange
            prefs[Keys.EAR_DISTORTION] = newState.earDistortion
            prefs[Keys.NOSE_DISTORTION] = newState.noseDistortion
            prefs[Keys.MOUTH_DISTORTION] = newState.mouthDistortion
            prefs[Keys.OVERALL_DAMAGE] = newState.overallDamage
            prefs[Keys.TOTAL_MINUTES] = newState.totalMinutes
            if (topAppsSummary.isNotBlank()) prefs[Keys.TOP_APPS_SUMMARY] = topAppsSummary
        }
    }
}
