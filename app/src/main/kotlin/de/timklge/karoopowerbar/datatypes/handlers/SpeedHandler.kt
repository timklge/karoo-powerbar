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
import de.timklge.karoopowerbar.Zone
import de.timklge.karoopowerbar.datatypes.BarHandler
import de.timklge.karoopowerbar.remap
import de.timklge.karoopowerbar.streamDataFlow
import de.timklge.karoopowerbar.streamSettings
import de.timklge.karoopowerbar.streamUserProfile
import de.timklge.karoopowerbar.throttle
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

class SpeedHandler(private val smoothed: Boolean) : BarHandler {
    override suspend fun preview(
        context: Context,
        karooSystem: KarooSystemService,
        powerbars: List<CustomProgressBar>
    ) {
        combine(karooSystem.streamUserProfile(), context.streamSettings()) { profile, settings ->
            profile to settings
        }.collectPreview(powerbars) { (profile, settings), fraction, powerbar ->
            val valueMetersPerSecond = interpolate(
                settings.minSpeed.toDouble(),
                settings.maxSpeed.toDouble(),
                fraction
            )
            val displayValue = when (profile.preferredUnit.distance) {
                UserProfile.PreferredUnit.UnitType.IMPERIAL -> valueMetersPerSecond * 2.23694
                else -> valueMetersPerSecond * 3.6
            }.roundToInt()
            val progress = remap(
                valueMetersPerSecond,
                settings.minSpeed.toDouble(),
                settings.maxSpeed.toDouble(),
                0.0,
                1.0
            ) ?: 0.0
            val zoneColorRes =
                Zone.entries[(progress * Zone.entries.size).roundToInt().coerceIn(0..<Zone.entries.size)].colorResource

            powerbar.progressColor = if (settings.useZoneColors) {
                context.getColor(zoneColorRes)
            } else {
                context.getColor(R.color.zone0)
            }
            powerbar.progress = progress
            powerbar.label = "$displayValue"
        }
    }

    override suspend fun handle(context: Context, karooSystem: KarooSystemService, powerbars: List<CustomProgressBar>) {
        val speedFlow = karooSystem.streamDataFlow(
            if (smoothed) DataType.Type.SMOOTHED_3S_AVERAGE_SPEED else DataType.Type.SPEED
        ).map { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
            .distinctUntilChanged()
        val settingsFlow = context.streamSettings()

        data class StreamData(val userProfile: UserProfile, val value: Double?, val settings: PowerbarSettings?)

        combine(
            karooSystem.streamUserProfile(),
            speedFlow,
            settingsFlow
        ) { userProfile, speed, settings ->
            StreamData(userProfile, speed, settings)
        }.distinctUntilChanged().throttle(1_000).collect { streamData ->
            val valueMetersPerSecond = streamData.value
            val value = when (streamData.userProfile.preferredUnit.distance) {
                UserProfile.PreferredUnit.UnitType.IMPERIAL -> valueMetersPerSecond?.times(2.23694)
                else -> valueMetersPerSecond?.times(3.6)
            }?.roundToInt()

            powerbars.forEach { powerbar ->
                if (value != null) {
                    val minSpeed = streamData.settings?.minSpeed ?: PowerbarSettings.defaultMinSpeedMs
                    val maxSpeed = streamData.settings?.maxSpeed ?: PowerbarSettings.defaultMaxSpeedMs
                    val progress = remap(
                        valueMetersPerSecond,
                        minSpeed.toDouble(),
                        maxSpeed.toDouble(),
                        0.0,
                        1.0
                    ) ?: 0.0
                    @ColorRes val zoneColorRes = Zone.entries[(progress * Zone.entries.size).roundToInt().coerceIn(0..<Zone.entries.size)].colorResource

                    powerbar.progressColor = if (streamData.settings?.useZoneColors == true) {
                        context.getColor(zoneColorRes)
                    } else {
                        context.getColor(R.color.zone0)
                    }
                    powerbar.progress = progress
                    powerbar.label = "$value"
                    Log.d(KarooPowerbarExtension.TAG, "Speed: $value min: $minSpeed max: $maxSpeed")
                } else {
                    powerbar.progressColor = context.getColor(R.color.zone0)
                    powerbar.progress = null
                    powerbar.label = "?"
                    Log.d(KarooPowerbarExtension.TAG, "Speed: Unavailable")
                }
                powerbar.invalidate()
            }
        }
    }
}