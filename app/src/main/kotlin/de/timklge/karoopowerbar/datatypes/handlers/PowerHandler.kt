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
import de.timklge.karoopowerbar.KarooPowerbarExtension.Companion.TAG
import de.timklge.karoopowerbar.PowerbarSettings
import de.timklge.karoopowerbar.R
import de.timklge.karoopowerbar.datatypes.BarHandler
import de.timklge.karoopowerbar.datatypes.Fields
import de.timklge.karoopowerbar.datatypes.PowerStreamSmoothing
import de.timklge.karoopowerbar.getZone
import de.timklge.karoopowerbar.remap
import de.timklge.karoopowerbar.streamDataFlow
import de.timklge.karoopowerbar.streamSettings
import de.timklge.karoopowerbar.streamUserProfile
import de.timklge.karoopowerbar.throttle
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

class PowerHandler(private val smoothed: PowerStreamSmoothing) : BarHandler {
    override suspend fun preview(
        context: Context,
        karooSystem: KarooSystemService,
        powerbars: List<CustomProgressBar>
    ) {
        combine(karooSystem.streamUserProfile(), context.streamSettings()) { profile, settings ->
            profile to settings
        }.collectPreview(powerbars) { (profile, settings), fraction, powerbar ->
            val minPower = (if (settings.useCustomPowerRange) {
                settings.minPower
            } else {
                null
            }) ?: profile.powerZones.first().min
            val maxPower = (if (settings.useCustomPowerRange) {
                settings.maxPower
            } else {
                null
            }) ?: (profile.powerZones.last().min + 30)
            val value = interpolate(minPower.toDouble(), maxPower.toDouble(), fraction).roundToInt()

            powerbar.minTarget = null
            powerbar.maxTarget = null
            powerbar.target = null
            powerbar.progressColor = if (settings.useZoneColors) {
                context.getColor(getZone(profile.powerZones, value)?.colorResource ?: R.color.zone7)
            } else {
                context.getColor(R.color.zone0)
            }
            powerbar.progress = remap(value.toDouble(), minPower.toDouble(), maxPower.toDouble(), 0.0, 1.0)
            powerbar.label = "${value}W"
        }
    }

    override suspend fun handle(
        context: Context,
        karooSystem: KarooSystemService,
        powerbars: List<CustomProgressBar>
    ) {
        val powerFlow = karooSystem.streamDataFlow(smoothed.dataTypeId)
            .map { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
            .distinctUntilChanged()

        val settingsFlow = context.streamSettings()

        val powerTargetFlow = karooSystem.streamDataFlow("TYPE_WORKOUT_POWER_TARGET_ID") // TYPE_WORKOUT_HEART_RATE_TARGET_ID, TYPE_WORKOUT_CADENCE_TARGET_ID,
            .map { (it as? StreamState.Streaming)?.dataPoint }
            .distinctUntilChanged()

        data class StreamData(val userProfile: UserProfile, val value: Double?, val settings: PowerbarSettings? = null, val powerTarget: DataPoint? = null)

        combine(karooSystem.streamUserProfile(), powerFlow, settingsFlow, powerTargetFlow) { userProfile, hr, settings, powerTarget ->
            StreamData(userProfile, hr, settings, powerTarget)
        }.distinctUntilChanged().throttle(1_000).collect { streamData ->
            val value = streamData.value?.roundToInt()

            powerbars.forEach { powerbar ->
                if (value != null) {
                    val customMinPower = if (streamData.settings?.useCustomPowerRange == true) streamData.settings.minPower else null
                    val customMaxPower = if (streamData.settings?.useCustomPowerRange == true) streamData.settings.maxPower else null
                    val minPower = customMinPower ?: streamData.userProfile.powerZones.first().min
                    val maxPower = customMaxPower ?: (streamData.userProfile.powerZones.last().min + 30)
                    val progress = remap(value.toDouble(), minPower.toDouble(), maxPower.toDouble(), 0.0, 1.0)

                    powerbar.minTarget = remap(streamData.powerTarget?.values?.get(Fields.FIELD_TARGET_MIN_ID), minPower.toDouble(), maxPower.toDouble(), 0.0, 1.0)
                    powerbar.maxTarget = remap(streamData.powerTarget?.values?.get(Fields.FIELD_TARGET_MAX_ID), minPower.toDouble(), maxPower.toDouble(), 0.0, 1.0)
                    powerbar.target = remap(streamData.powerTarget?.values?.get(Fields.FIELD_TARGET_VALUE_ID), minPower.toDouble(), maxPower.toDouble(), 0.0, 1.0)

                    powerbar.progressColor = if (streamData.settings?.useZoneColors == true) {
                        context.getColor(getZone(streamData.userProfile.powerZones, value)?.colorResource ?: R.color.zone7)
                    } else {
                        context.getColor(R.color.zone0)
                    }
                    powerbar.progress = progress
                    powerbar.label = "${value}W"

                    Log.d(TAG, "Power: $value min: $minPower max: $maxPower")
                } else {
                    powerbar.progressColor = context.getColor(R.color.zone0)
                    powerbar.progress = null
                    powerbar.label = "?"

                    Log.d(TAG, "Power: Unavailable")
                }
                powerbar.invalidate()
            }
        }
    }
}