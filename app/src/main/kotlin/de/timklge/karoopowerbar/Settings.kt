package de.timklge.karoopowerbar

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.timklge.karoopowerbar.datatypes.SelectedSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings", corruptionHandler = ReplaceFileCorruptionHandler {
    Log.w(KarooPowerbarExtension.TAG, "Error reading settings, using default values")
    emptyPreferences()
})

val settingsKey = stringPreferencesKey("settings")

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
    val barBackground: Boolean = false,
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
    val useCustomPowerRange: Boolean = false
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

fun Context.streamSettings(): Flow<PowerbarSettings> {
    return dataStore.data.map { settingsJson ->
        try {
            jsonWithUnknownKeys.decodeFromString<PowerbarSettings>(
                settingsJson[settingsKey] ?: PowerbarSettings.defaultSettings
            )
        } catch(e: Throwable){
            Log.e(KarooPowerbarExtension.TAG, "Failed to read preferences", e)
            jsonWithUnknownKeys.decodeFromString<PowerbarSettings>(PowerbarSettings.defaultSettings)
        }
    }.distinctUntilChanged()
}