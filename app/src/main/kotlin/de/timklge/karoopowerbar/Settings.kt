package de.timklge.karoopowerbar

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.timklge.karoopowerbar.screens.SelectedSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val settingsKey = stringPreferencesKey("settings")

@Serializable
data class PowerbarSettings(
    val source: SelectedSource = SelectedSource.POWER,
    val topBarSource: SelectedSource = SelectedSource.NONE,
    val onlyShowWhileRiding: Boolean = true,
    val showLabelOnBars: Boolean = true,
    val useZoneColors: Boolean = true,
    val barSize: CustomProgressBarSize = CustomProgressBarSize.MEDIUM,

    val minCadence: Int = defaultMinCadence, val maxCadence: Int = defaultMaxCadence,
    val minSpeed: Float = defaultMinSpeedMs, val maxSpeed: Float = defaultMaxSpeedMs, // 50 km/h in m/s
    val minPower: Int? = null, val maxPower: Int? = null,
    val minHr: Int? = null, val maxHr: Int? = null,
    val useCustomHrRange: Boolean = false, val useCustomPowerRange: Boolean = false
){
    companion object {
        val defaultSettings = Json.encodeToString(PowerbarSettings())
        const val defaultMinSpeedMs = 0f
        const val defaultMaxSpeedMs = 13.89f
        const val defaultMinCadence = 50
        const val defaultMaxCadence = 120
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