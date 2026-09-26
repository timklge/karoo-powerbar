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
import de.timklge.karoopowerbar.datatypes.BarHandler
import de.timklge.karoopowerbar.getZone
import de.timklge.karoopowerbar.remap
import de.timklge.karoopowerbar.streamDataFlow
import de.timklge.karoopowerbar.streamSettings
import de.timklge.karoopowerbar.throttle
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

class PedalSmoothnessHandler : BarHandler {
    override suspend fun handle(context: Context, karooSystem: KarooSystemService, powerbars: List<CustomProgressBar>) {
        data class StreamData(
            val left: Double?,
            val right: Double?,
            val power: Double?,
            val settings: PowerbarSettings?
        )

        val pedalSmoothnessFlow = karooSystem.streamDataFlow(DataType.Type.PEDAL_SMOOTHNESS)
        combine(pedalSmoothnessFlow, context.streamSettings()) { pedalSmoothness, settings ->
            val values = (pedalSmoothness as? StreamState.Streaming)?.dataPoint?.values
            StreamData(
                values?.get(DataType.Field.PEDAL_SMOOTHNESS_LEFT),
                values?.get(DataType.Field.PEDAL_SMOOTHNESS_RIGHT),
                values?.get(DataType.Field.POWER),
                settings
            )
        }.distinctUntilChanged().throttle(1_000).collect { streamData ->
            val left = streamData.left?.coerceIn(0.0, 100.0)
            val right = streamData.right?.coerceIn(0.0, 100.0)
            val average = if (left != null && right != null) (left + right) / 2.0 else right ?: left

            powerbars.forEach { powerbar ->
                if (average != null) {
                    val minSmoothness = streamData.settings?.minPedalSmoothness
                        ?: PowerbarSettings.defaultMinPedalSmoothnessPercent
                    val maxSmoothness = streamData.settings?.maxPedalSmoothness
                        ?: PowerbarSettings.defaultMaxPedalSmoothnessPercent
                    val zoneValue = remap(
                        average,
                        minSmoothness.toDouble(),
                        maxSmoothness.toDouble(),
                        1.0,
                        0.0
                    )
                        ?.coerceIn(0.0, 1.0) ?: 0.0

                    powerbar.progressColor = context.getColor(getZone(zoneValue).colorResource)
                    powerbar.progress = remap(
                        average,
                        minSmoothness.toDouble(),
                        maxSmoothness.toDouble(),
                        0.0,
                        1.0
                    )
                        ?.coerceIn(0.0, 1.0)
                    powerbar.label = if (left != null && right != null && left.roundToInt() != right.roundToInt()) {
                        "${left.roundToInt()}-${right.roundToInt()}"
                    } else {
                        "${average.roundToInt()}"
                    }
                    Log.d(KarooPowerbarExtension.TAG, "Pedal Smoothness: $left-$right power: ${streamData.power}")
                } else {
                    powerbar.progressColor = context.getColor(R.color.zone0)
                    powerbar.progress = null
                    powerbar.label = "?"
                    Log.d(KarooPowerbarExtension.TAG, "Pedal Smoothness: Unavailable")
                }
                powerbar.invalidate()
            }
        }
    }
}