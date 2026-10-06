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
import de.timklge.karoopowerbar.R
import de.timklge.karoopowerbar.datatypes.BarHandler
import de.timklge.karoopowerbar.datatypes.FlightAttendantSuspensionLocation
import de.timklge.karoopowerbar.datatypes.FlightAttendantSuspensionStateValue
import de.timklge.karoopowerbar.streamDataFlow
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FlightAttendantSuspensionStateHandler(private val location: FlightAttendantSuspensionLocation) :
    BarHandler {
    private val values = FlightAttendantSuspensionStateValue.entries.associateBy { it.value }

    override suspend fun preview(
        context: Context,
        karooSystem: KarooSystemService,
        powerbars: List<CustomProgressBar>
    ) {
        var previousIndex = -1
        flowOf(Unit).collectPreview(powerbars) { _, fraction, powerbar ->
            var index = (fraction * FlightAttendantSuspensionStateValue.entries.size).toInt()
            if (index == previousIndex) {
                index = (index + 1) % FlightAttendantSuspensionStateValue.entries.size
            }
            previousIndex = index
            val state = FlightAttendantSuspensionStateValue.entries[index]

            powerbar.progressColor = context.getColor(state.colorResId)
            powerbar.progress = 0.0
            powerbar.label = context.getString(state.labelResId)
        }
    }

    override suspend fun handle(context: Context, karooSystem: KarooSystemService, powerbars: List<CustomProgressBar>) {
        karooSystem.streamDataFlow(location.dataTypeId)
            .map { (it as? StreamState.Streaming)?.dataPoint?.values?.get(location.stateFieldId)?.toInt() }
            .distinctUntilChanged()
            .collect { stateValue ->
                powerbars.forEach { powerbar ->
                    if (stateValue != null) {
                        val state = values[stateValue]
                        val label = state?.let { context.getString(it.labelResId) } ?: "?"
                        powerbar.progressColor = context.getColor(state?.colorResId ?: R.color.zone0)
                        powerbar.progress = 0.0
                        powerbar.label = label
                        Log.d(KarooPowerbarExtension.TAG, "Suspension ${location.name}: $label")
                    } else {
                        powerbar.progressColor = context.getColor(R.color.zone0)
                        powerbar.progress = null
                        powerbar.label = "?"
                        Log.d(KarooPowerbarExtension.TAG, "Suspension ${location.name}: Unavailable")
                    }
                    powerbar.invalidate()
                }
            }
    }
}