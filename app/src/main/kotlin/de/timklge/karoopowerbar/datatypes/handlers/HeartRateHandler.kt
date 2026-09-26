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
import de.timklge.karoopowerbar.CustomProgressBar
import de.timklge.karoopowerbar.KarooPowerbarExtension
import de.timklge.karoopowerbar.PowerbarSettings
import de.timklge.karoopowerbar.R
import de.timklge.karoopowerbar.Window
import de.timklge.karoopowerbar.datatypes.BarHandler
import de.timklge.karoopowerbar.datatypes.Fields
import de.timklge.karoopowerbar.getZone
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

class HeartRateHandler : BarHandler {
    override suspend fun handle(context: Context, karooSystem: KarooSystemService, powerbars: List<CustomProgressBar>) {
        val heartRateFlow = karooSystem.streamDataFlow(DataType.Type.HEART_RATE)
            .map { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
            .distinctUntilChanged()

        val settingsFlow = context.streamSettings()
        val targetFlow = karooSystem.streamDataFlow("TYPE_WORKOUT_HEART_RATE_TARGET_ID")
            .map { (it as? StreamState.Streaming)?.dataPoint }
            .distinctUntilChanged()

        data class StreamData(
            val userProfile: UserProfile,
            val value: Double?,
            val settings: PowerbarSettings?,
            val target: DataPoint?
        )

        combine(
            karooSystem.streamUserProfile(),
            heartRateFlow,
            settingsFlow,
            targetFlow
        ) { userProfile, heartRate, settings, target ->
            StreamData(userProfile, heartRate, settings, target)
        }.distinctUntilChanged().throttle(1_000).collect { streamData ->
            val value = streamData.value?.roundToInt()
            powerbars.forEach { powerbar ->
                if (value != null) {
                    val customMinHr = if (streamData.settings?.useCustomHrRange == true) streamData.settings.minHr else null
                    val customMaxHr = if (streamData.settings?.useCustomHrRange == true) streamData.settings.maxHr else null
                    val minHr = customMinHr ?: streamData.userProfile.restingHr
                    val maxHr = customMaxHr ?: streamData.userProfile.maxHr
                    val progress =
                        remap(value.toDouble(), minHr.toDouble(), maxHr.toDouble(), 0.0, 1.0)

                    powerbar.minTarget = remap(
                        streamData.target?.values?.get(Fields.FIELD_TARGET_MIN_ID),
                        minHr.toDouble(),
                        maxHr.toDouble(),
                        0.0,
                        1.0
                    )
                    powerbar.maxTarget = remap(
                        streamData.target?.values?.get(Fields.FIELD_TARGET_MAX_ID),
                        minHr.toDouble(),
                        maxHr.toDouble(),
                        0.0,
                        1.0
                    )
                    powerbar.target = remap(
                        streamData.target?.values?.get(Fields.FIELD_TARGET_VALUE_ID),
                        minHr.toDouble(),
                        maxHr.toDouble(),
                        0.0,
                        1.0
                    )

                    powerbar.progressColor = if (streamData.settings?.useZoneColors == true) {
                        context.getColor(getZone(streamData.userProfile.heartRateZones, value)?.colorResource ?: R.color.zone7)
                    } else {
                        context.getColor(R.color.zone0)
                    }
                    powerbar.progress = progress
                    powerbar.label = "$value"
                    Log.d(KarooPowerbarExtension.TAG, "Hr: $value min: $minHr max: $maxHr")
                } else {
                    powerbar.progressColor = context.getColor(R.color.zone0)
                    powerbar.progress = null
                    powerbar.label = "?"
                    Log.d(KarooPowerbarExtension.TAG, "Hr: Unavailable")
                }
                powerbar.invalidate()
            }
        }
    }
}