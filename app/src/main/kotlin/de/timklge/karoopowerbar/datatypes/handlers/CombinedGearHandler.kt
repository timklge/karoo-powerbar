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

class CombinedGearHandler : BarHandler {
    override suspend fun handle(context: Context, karooSystem: KarooSystemService, powerbars: List<CustomProgressBar>) {
        data class GearState(
            val frontGear: Int?,
            val frontMax: Int?,
            val rearGear: Int?,
            val rearMax: Int?,
            val colorize: Boolean
        )
        data class SettingsAndState(
            val settings: PowerbarSettings,
            val front: StreamState?,
            val rear: StreamState?
        )

        combine(
            context.streamSettings(),
            karooSystem.streamDataFlow(Gears.FRONT.dataTypeId),
            karooSystem.streamDataFlow(Gears.REAR.dataTypeId)
        ) { settings, front, rear ->
            SettingsAndState(settings, front, rear)
        }.map { (settings, front, rear) ->
            val frontValues = (front as? StreamState.Streaming)?.dataPoint?.values
            val rearValues = (rear as? StreamState.Streaming)?.dataPoint?.values
            GearState(
                frontGear = frontValues?.get(Gears.FRONT.numberFieldId)?.toInt(),
                frontMax = frontValues?.get(Gears.FRONT.maxFieldId)?.toInt(),
                rearGear = rearValues?.get(Gears.REAR.numberFieldId)?.toInt(),
                rearMax = rearValues?.get(Gears.REAR.maxFieldId)?.toInt(),
                colorize = settings.useZoneColors
            )
        }.distinctUntilChanged().collect { gearState ->
            powerbars.forEach { powerbar ->
                if (gearState.frontGear != null && gearState.rearGear != null) {
                    val frontMax = gearState.frontMax ?: gearState.frontGear
                    val rearMax = gearState.rearMax ?: gearState.rearGear
                    val frontProgress = remap(gearState.frontGear.toDouble(), 1.0, frontMax.toDouble(), 0.0, 1.0) ?: 0.0
                    val rearProgress = remap(gearState.rearGear.toDouble(), 1.0, rearMax.toDouble(), 1.0, 0.0) ?: 0.0
                    val progress = ((frontProgress + rearProgress) / 2.0).coerceIn(0.0, 1.0)

                    powerbar.progressColor = if (gearState.colorize) {
                        context.getColor(getZone(progress).colorResource)
                    } else {
                        context.getColor(R.color.zone0)
                    }
                    powerbar.progress = progress
                    powerbar.label = "F${gearState.frontGear}-R${gearState.rearGear}"
                    Log.d(TAG, "Gears Combined: F${gearState.frontGear}/$frontMax R${gearState.rearGear}/$rearMax")
                } else if (gearState.frontGear != null || gearState.rearGear != null) {
                    powerbar.progressColor = context.getColor(R.color.zone0)
                    powerbar.progress = null
                    val frontPart = gearState.frontGear?.let { "F$it" } ?: "F?"
                    val rearPart = gearState.rearGear?.let { "R$it" } ?: "R?"
                    powerbar.label = "$frontPart-$rearPart"
                    Log.d(TAG, "Gears Combined: Partial")
                } else {
                    powerbar.progressColor = context.getColor(R.color.zone0)
                    powerbar.progress = null
                    powerbar.label = "?"
                    Log.d(TAG, "Gears Combined: Unavailable")
                }
                powerbar.invalidate()
            }
        }
    }
}
