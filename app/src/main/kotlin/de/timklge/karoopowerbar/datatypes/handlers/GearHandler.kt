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
import de.timklge.karoopowerbar.datatypes.Gears
import de.timklge.karoopowerbar.getZone
import de.timklge.karoopowerbar.remap
import de.timklge.karoopowerbar.streamDataFlow
import de.timklge.karoopowerbar.streamSettings
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class GearHandler(private val gears: Gears) : BarHandler {
    override suspend fun preview(
        context: Context,
        karooSystem: KarooSystemService,
        powerbars: List<CustomProgressBar>
    ) {
        context.streamSettings().collectPreview(powerbars) { settings, fraction, powerbar ->
            val maxGear = if (gears == Gears.FRONT) 3 else 12
            val currentGear = interpolate(1.0, maxGear + 1.0, fraction).toInt()
            val progress = remap(currentGear.toDouble(), 1.0, maxGear.toDouble(), 0.0, 1.0)
            powerbar.progressColor = if (settings.useZoneColors) {
                progress?.let { context.getColor(getZone(it).colorResource) }
                    ?: context.getColor(R.color.zone0)
            } else {
                context.getColor(R.color.zone0)
            }
            powerbar.progress = progress
            powerbar.label = "${gears.prefix}$currentGear"
        }
    }

    override suspend fun handle(context: Context, karooSystem: KarooSystemService, powerbars: List<CustomProgressBar>) {
        data class GearState(val currentGear: Int?, val maxGear: Int?, val colorize: Boolean)
        data class SettingsAndState(val settings: PowerbarSettings, val streamState: StreamState?)

        combine(
            context.streamSettings(),
            karooSystem.streamDataFlow(gears.dataTypeId)
        ) { settings, streamState ->
            SettingsAndState(settings, streamState)
        }.map { (settings, streamState) ->
            val values = (streamState as? StreamState.Streaming)?.dataPoint?.values
            values?.let {
                GearState(
                    values[gears.numberFieldId]?.toInt(),
                    values[gears.maxFieldId]?.toInt(),
                    settings.useZoneColors
                )
            }
        }.distinctUntilChanged().collect { gearState ->
            powerbars.forEach { powerbar ->
                if (gearState?.currentGear != null) {
                    val currentGear = gearState.currentGear
                    val maxGear = gearState.maxGear ?: currentGear
                    val progress = remap(currentGear.toDouble(), 1.0, maxGear.toDouble(), 0.0, 1.0)
                    powerbar.progressColor = if (gearState.colorize) {
                        progress?.let { context.getColor(getZone(it).colorResource) } ?: context.getColor(
                            R.color.zone0)
                    } else {
                        context.getColor(R.color.zone0)
                    }
                    powerbar.progress = progress
                    powerbar.label = "${gears.prefix}$currentGear"
                    Log.d(KarooPowerbarExtension.TAG, "Gears ${gears.name}: $currentGear/$maxGear")
                } else {
                    powerbar.progressColor = context.getColor(R.color.zone0)
                    powerbar.progress = null
                    powerbar.label = "?"
                    Log.d(KarooPowerbarExtension.TAG, "Gears ${gears.name}: Unavailable")
                }
                powerbar.invalidate()
            }
        }
    }
}