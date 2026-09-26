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

package de.timklge.karoopowerbar.datatypes.handlers

import android.content.Context
import android.util.Log
import androidx.annotation.ColorRes
import de.timklge.karoopowerbar.CustomProgressBar
import de.timklge.karoopowerbar.KarooPowerbarExtension
import de.timklge.karoopowerbar.PowerbarSettings
import de.timklge.karoopowerbar.R
import de.timklge.karoopowerbar.Window
import de.timklge.karoopowerbar.Zone
import de.timklge.karoopowerbar.datatypes.BarHandler
import de.timklge.karoopowerbar.datatypes.Fields
import de.timklge.karoopowerbar.remap
import de.timklge.karoopowerbar.streamDataFlow
import de.timklge.karoopowerbar.streamSettings
import de.timklge.karoopowerbar.streamUserProfile
import de.timklge.karoopowerbar.throttle
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

class CadenceHandler(private val smoothed: Boolean) : BarHandler {
    override suspend fun handle(context: Context, karooSystem: KarooSystemService, powerbars: List<CustomProgressBar>) {
        val cadenceFlow = karooSystem.streamDataFlow(
            if (smoothed) DataType.Type.SMOOTHED_3S_AVERAGE_CADENCE else DataType.Type.CADENCE
        ).map { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
            .distinctUntilChanged()

        data class StreamData(
            val userProfile: UserProfile,
            val value: Double?,
            val settings: PowerbarSettings?,
            val cadenceTarget: DataPoint?
        )

        val settingsFlow = context.streamSettings()
        val cadenceTargetFlow = karooSystem.streamDataFlow("TYPE_WORKOUT_CADENCE_TARGET_ID")
            .map { (it as? StreamState.Streaming)?.dataPoint }
            .distinctUntilChanged()

        combine(
            karooSystem.streamUserProfile(),
            cadenceFlow,
            settingsFlow,
            cadenceTargetFlow
        ) { userProfile, cadence, settings, cadenceTarget ->
            StreamData(userProfile, cadence, settings, cadenceTarget)
        }.distinctUntilChanged().throttle(1_000).collect { streamData ->
            val value = streamData.value?.roundToInt()
            powerbars.forEach { powerbar ->
                if (value != null) {
                    val minCadence = streamData.settings?.minCadence ?: PowerbarSettings.defaultMinCadence
                    val maxCadence = streamData.settings?.maxCadence ?: PowerbarSettings.defaultMaxCadence
                    val progress = remap(
                        value.toDouble(),
                        minCadence.toDouble(),
                        maxCadence.toDouble(),
                        0.0,
                        1.0
                    ) ?: 0.0

                    powerbar.minTarget = remap(
                        streamData.cadenceTarget?.values?.get(Fields.FIELD_TARGET_MIN_ID),
                        minCadence.toDouble(),
                        maxCadence.toDouble(),
                        0.0,
                        1.0
                    )
                    powerbar.maxTarget = remap(
                        streamData.cadenceTarget?.values?.get(Fields.FIELD_TARGET_MAX_ID),
                        minCadence.toDouble(),
                        maxCadence.toDouble(),
                        0.0,
                        1.0
                    )
                    powerbar.target = remap(
                        streamData.cadenceTarget?.values?.get(Fields.FIELD_TARGET_VALUE_ID),
                        minCadence.toDouble(),
                        maxCadence.toDouble(),
                        0.0,
                        1.0
                    )

                    @ColorRes val zoneColorRes = Zone.entries[(progress * Zone.entries.size).roundToInt().coerceIn(0..<Zone.entries.size)].colorResource
                    powerbar.progressColor = if (streamData.settings?.useZoneColors == true) {
                        context.getColor(zoneColorRes)
                    } else {
                        context.getColor(R.color.zone0)
                    }
                    powerbar.progress = progress
                    powerbar.label = "$value"
                    Log.d(KarooPowerbarExtension.TAG, "Cadence: $value min: $minCadence max: $maxCadence")
                } else {
                    powerbar.progressColor = context.getColor(R.color.zone0)
                    powerbar.progress = null
                    powerbar.label = "?"
                    Log.d(KarooPowerbarExtension.TAG, "Cadence: Unavailable")
                }
                powerbar.invalidate()
            }
        }
    }
}