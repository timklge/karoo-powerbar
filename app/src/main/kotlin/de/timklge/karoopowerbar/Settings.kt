/*
 * Copyright 2024-2026 karoo-powerbar contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.timklge.karoopowerbar

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.timklge.karoopowerbar.datatypes.SelectedSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings", corruptionHandler = ReplaceFileCorruptionHandler {
    Log.w(KarooPowerbarExtension.TAG, "Error reading settings, using default values")
    emptyPreferences()
})

val settingsKey = stringPreferencesKey("settings")
val profileSettingsKey = stringPreferencesKey("profile_settings")

@Serializable
data class PowerbarSettings(
    @SerialName("source") val bottomBarSource: SelectedSource = SelectedSource.POWER,
    val topBarSource: SelectedSource = SelectedSource.NONE,

    val splitTopBar: Boolean = false,
    val splitBottomBar: Boolean = false,
    val topBarLeftSource: SelectedSource = SelectedSource.NONE,
    val topBarRightSource: SelectedSource = SelectedSource.NONE,
    val bottomBarLeftSource: SelectedSource = SelectedSource.POWER,
    val bottomBarRightSource: SelectedSource = SelectedSource.NONE,

    val onlyShowWhileRiding: Boolean = true,
    val showLabelOnBars: Boolean = true,
    val useZoneColors: Boolean = true,
    val barSize: CustomProgressBarSize = CustomProgressBarSize.MEDIUM,
    val barFontSize: CustomProgressBarFontSize = CustomProgressBarFontSize.fromSize(barSize),
    val barBarSize: CustomProgressBarBarSize = CustomProgressBarBarSize.fromSize(barSize),

    val minCadence: Int = defaultMinCadence, val maxCadence: Int = defaultMaxCadence,
    val minSpeed: Float = defaultMinSpeedMs, val maxSpeed: Float = defaultMaxSpeedMs, // 50 km/h in m/s
    val minPower: Int? = null, val maxPower: Int? = null,
    val minHr: Int? = null, val maxHr: Int? = null,
    val minGradient: Int? = defaultMinGradient, val maxGradient: Int? = defaultMaxGradient,
    val minPedalSmoothness: Float? = defaultMinPedalSmoothnessPercent, val maxPedalSmoothness: Float? = defaultMaxPedalSmoothnessPercent,

    val useCustomGradientRange: Boolean = false,
    val useCustomHrRange: Boolean = false,
    val useCustomPowerRange: Boolean = false,

    val stickToEdge: Boolean = false,

    val enableUpdateNotifications: Boolean = true,
){
    companion object {
        val defaultSettings = Json.encodeToString(PowerbarSettings())
        const val defaultMinSpeedMs = 0f
        const val defaultMaxSpeedMs = 13.89f
        const val defaultMinCadence = 50
        const val defaultMaxCadence = 120
        const val defaultMinGradient = 0
        const val defaultMaxGradient = 15
        const val defaultMinPedalSmoothnessPercent = 10f
        const val defaultMaxPedalSmoothnessPercent = 40f
    }
}

/** Default settings plus optional settings that only apply to a single karoo ride profile (keyed by profile name). */
data class StoredSettings(
    val default: PowerbarSettings,
    val profiles: Map<String, PowerbarSettings>,
) {
    /** Returns the stored key of the profile with the given name, ignoring case and surrounding whitespace. */
    fun findProfileKey(name: String?): String? {
        val trimmed = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return profiles.keys.firstOrNull { it.trim().equals(trimmed, ignoreCase = true) }
    }

    /** Settings used while riding with the given ride profile: its own settings if present, otherwise the defaults. */
    fun resolve(rideProfileName: String?): PowerbarSettings {
        return findProfileKey(rideProfileName)?.let { profiles[it] } ?: default
    }

    /** Settings of exactly this entry (null = defaults). Null if the profile has no settings. */
    fun exact(profileKey: String?): PowerbarSettings? {
        return if (profileKey == null) default else profiles[profileKey]
    }
}

/** Process-wide state that decides which settings entry [streamSettings] resolves to. */
object SettingsTarget {
    /** Name of the currently active karoo ride profile, as reported by the karoo system. */
    val activeRideProfileName = MutableStateFlow<String?>(null)

    /** While the settings screen is open: the settings entry being edited (null profile = defaults). */
    val previewTarget = MutableStateFlow<PreviewTarget?>(null)

    data class PreviewTarget(val profileKey: String?)
}

private val profileMapSerializer = MapSerializer(String.serializer(), PowerbarSettings.serializer())

fun Context.streamStoredSettings(onError: ((Throwable) -> Unit)? = null): Flow<StoredSettings> {
    return dataStore.data.map { prefs ->
        val default = try {
            jsonWithUnknownKeys.decodeFromString<PowerbarSettings>(
                prefs[settingsKey] ?: PowerbarSettings.defaultSettings
            )
        } catch (e: Throwable) {
            Log.e(KarooPowerbarExtension.TAG, "Failed to read preferences", e)
            onError?.invoke(e)
            jsonWithUnknownKeys.decodeFromString<PowerbarSettings>(PowerbarSettings.defaultSettings)
        }

        StoredSettings(default, prefs.readProfiles(onError))
    }.distinctUntilChanged()
}

/** Settings that should currently be applied to the bars. */
fun Context.streamSettings(onError: ((Throwable) -> Unit)? = null): Flow<PowerbarSettings> {
    return combine(
        streamStoredSettings(onError),
        SettingsTarget.previewTarget,
        SettingsTarget.activeRideProfileName,
    ) { stored, preview, activeProfile ->
        if (preview != null) {
            stored.exact(preview.profileKey) ?: stored.default
        } else {
            stored.resolve(activeProfile)
        }
    }.distinctUntilChanged()
}

private fun Preferences.readProfiles(onError: ((Throwable) -> Unit)? = null): Map<String, PowerbarSettings> {
    val json = this[profileSettingsKey] ?: return emptyMap()
    return try {
        jsonWithUnknownKeys.decodeFromString(profileMapSerializer, json)
    } catch (e: Throwable) {
        Log.e(KarooPowerbarExtension.TAG, "Failed to read profile preferences", e)
        onError?.invoke(e)
        emptyMap()
    }
}

private fun MutablePreferences.writeProfiles(profiles: Map<String, PowerbarSettings>) {
    if (profiles.isEmpty()) {
        remove(profileSettingsKey)
    } else {
        this[profileSettingsKey] = Json.encodeToString(profileMapSerializer, profiles)
    }
}

/** Saves the settings of the given entry (null = defaults). Profile settings that do not exist (anymore) are not re-created. */
suspend fun Context.saveSettings(profileKey: String?, settings: PowerbarSettings) {
    dataStore.edit { prefs ->
        if (profileKey == null) {
            prefs[settingsKey] = Json.encodeToString(settings)
        } else {
            val profiles = prefs.readProfiles()
            if (profiles.containsKey(profileKey)) {
                prefs.writeProfiles(profiles + (profileKey to settings))
            }
        }
    }
}

/** Creates settings for a ride profile. Returns the stored profile key, or null if the name is blank or already used. */
suspend fun Context.addProfileSettings(name: String, settings: PowerbarSettings): String? {
    val key = name.trim()
    if (key.isEmpty()) return null

    var created = false
    dataStore.edit { prefs ->
        val profiles = prefs.readProfiles()
        if (profiles.keys.none { it.trim().equals(key, ignoreCase = true) }) {
            prefs.writeProfiles(profiles + (key to settings))
            created = true
        }
    }
    return key.takeIf { created }
}

suspend fun Context.deleteProfileSettings(profileKey: String) {
    dataStore.edit { prefs ->
        prefs.writeProfiles(prefs.readProfiles() - profileKey)
    }
}

