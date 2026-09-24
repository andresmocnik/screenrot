package com.screenrot.app.debug

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.screenrot.app.data.CharacterStateStore
import com.screenrot.core.AppProfileRegistry
import com.screenrot.core.AppUsage
import com.screenrot.core.DamageEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.debugDataStore by preferencesDataStore(name = "screenrot_debug_state")

/**
 * DEBUG-ONLY. Lets a developer set fake per-app minutes and immediately see the resulting
 * character, instead of using the phone for real for 6 hours. Wired only into a debug-build
 * screen (see BuildConfig.DEBUG gating in the UI) — never exposed in the shipped onboarding
 * flow. Persists its fake minutes separately from the real UsageStats-backed store so flipping
 * back to "real mode" doesn't require clearing anything.
 */
class DebugUsageSimulator(private val context: Context) {

    private object Keys {
        val FAKE_USAGE_JSON = stringPreferencesKey("fake_usage_json")
        val ENABLED = intPreferencesKey("debug_mode_enabled") // 0/1, DataStore has no boolean-free helper we need here
    }

    private val registry = AppProfileRegistry()
    private val realStore = CharacterStateStore(context)

    val isEnabled: Flow<Boolean> = context.debugDataStore.data.map { (it[Keys.ENABLED] ?: 0) == 1 }

    val fakeUsage: Flow<List<AppUsage>> = context.debugDataStore.data.map { prefs ->
        parseUsage(prefs[Keys.FAKE_USAGE_JSON] ?: "[]")
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.debugDataStore.edit { it[Keys.ENABLED] = if (enabled) 1 else 0 }
    }

    suspend fun setAppMinutes(packageName: String, displayName: String, minutes: Int) {
        val current = fakeUsage.first().toMutableList()
        val idx = current.indexOfFirst { it.packageName == packageName }
        val entry = AppUsage(packageName, displayName, minutes.coerceAtLeast(0))
        if (idx >= 0) current[idx] = entry else current.add(entry)
        persist(current)
        applyToRealStore(current)
    }

    suspend fun addMinutes(packageName: String, displayName: String, delta: Int) {
        val current = fakeUsage.first()
        val existing = current.find { it.packageName == packageName }?.minutes ?: 0
        setAppMinutes(packageName, displayName, existing + delta)
    }

    suspend fun resetDay() {
        persist(emptyList())
        realStore.save(com.screenrot.core.CharacterState.PRISTINE)
    }

    private suspend fun applyToRealStore(usage: List<AppUsage>) {
        val state = DamageEngine.compute(usage, registry)
        val summary = usage.sortedByDescending { it.minutes }.take(3)
            .joinToString(", ") { "${it.displayName} ${it.minutes}m" }
        realStore.save(state, summary)
    }

    private suspend fun persist(usage: List<AppUsage>) {
        val arr = JSONArray()
        usage.forEach {
            arr.put(JSONObject().apply {
                put("pkg", it.packageName)
                put("name", it.displayName)
                put("minutes", it.minutes)
            })
        }
        context.debugDataStore.edit { it[Keys.FAKE_USAGE_JSON] = arr.toString() }
    }

    private fun parseUsage(json: String): List<AppUsage> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            AppUsage(o.getString("pkg"), o.getString("name"), o.getInt("minutes"))
        }
    }

    companion object {
        // A few convenient presets for the debug screen's quick-add buttons.
        val PRESET_APPS = listOf(
            "com.zhiliaoapp.musically" to "TikTok",
            "com.instagram.android" to "Instagram",
            "com.google.android.youtube" to "YouTube",
            "com.whatsapp" to "WhatsApp"
        )
    }
}
