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
import de.timklge.karoopowerbar.ProgressBarDrawMode
import de.timklge.karoopowerbar.R
import de.timklge.karoopowerbar.datatypes.BarHandler
import de.timklge.karoopowerbar.datatypes.PedalBalanceSmoothing
import de.timklge.karoopowerbar.remap
import de.timklge.karoopowerbar.streamDataFlow
import de.timklge.karoopowerbar.throttle
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

class PowerBalanceHandler(private val smoothing: PedalBalanceSmoothing) : BarHandler {
    override suspend fun preview(
        context: Context,
        karooSystem: KarooSystemService,
        powerbars: List<CustomProgressBar>
    ) {
        flowOf(Unit).collectPreview(powerbars) { _, fraction, powerbar ->
            powerbar.drawMode = ProgressBarDrawMode.CENTER_OUT
            val leftPercent = interpolate(40.0, 60.0, fraction)
            val value = remap(leftPercent.coerceIn(0.0, 100.0), 40.0, 60.0, 100.0, 0.0)
            val roundedLeft = leftPercent.roundToInt()
            val zoneColorRes = when {
                roundedLeft > 50 -> R.color.zone0
                roundedLeft == 50 -> R.color.zone1
                else -> R.color.zone7
            }

            powerbar.progressColor = context.getColor(zoneColorRes)
            powerbar.progress = value?.div(100.0)
            powerbar.label = "$roundedLeft-${100 - roundedLeft}"
        }
    }

    override suspend fun handle(context: Context, karooSystem: KarooSystemService, powerbars: List<CustomProgressBar>) {
        data class StreamData(val left: Double?, val power: Double?)

        karooSystem.streamDataFlow(smoothing.dataTypeId)
            .map {
                val values = (it as? StreamState.Streaming)?.dataPoint?.values
                StreamData(values?.get(DataType.Field.PEDAL_POWER_BALANCE_LEFT), values?.get(
                    DataType.Field.POWER))
            }
            .distinctUntilChanged()
            .throttle(1_000)
            .collect { streamData ->
                powerbars.forEach { powerbar ->
                    powerbar.drawMode = ProgressBarDrawMode.CENTER_OUT
                    if (streamData.left != null) {
                        val value =
                            remap(streamData.left.coerceIn(0.0, 100.0), 40.0, 60.0, 100.0, 0.0)
                        val percentLeft = streamData.left.roundToInt()
                        @ColorRes val zoneColorRes = if (percentLeft > 50) {
                            R.color.zone0
                        } else if (percentLeft == 50) {
                            R.color.zone1
                        } else {
                            R.color.zone7
                        }

                        powerbar.progressColor = context.getColor(zoneColorRes)
                        powerbar.progress = value?.div(100.0)
                        powerbar.label = "$percentLeft-${100 - percentLeft}"
                        Log.d(KarooPowerbarExtension.TAG, "Balance: ${streamData.left} power: ${streamData.power}")
                    } else {
                        powerbar.progressColor = context.getColor(R.color.zone0)
                        powerbar.progress = null
                        powerbar.label = "?"
                        Log.d(KarooPowerbarExtension.TAG, "Balance: Unavailable")
                    }
                    powerbar.invalidate()
                }
            }
    }
}