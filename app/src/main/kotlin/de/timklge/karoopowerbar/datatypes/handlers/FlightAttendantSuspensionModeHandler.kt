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
import de.timklge.karoopowerbar.Window
import de.timklge.karoopowerbar.datatypes.BarHandler
import de.timklge.karoopowerbar.datatypes.Fields
import de.timklge.karoopowerbar.datatypes.FlightAttendantSuspensionMode
import de.timklge.karoopowerbar.streamDataFlow
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FlightAttendantSuspensionModeHandler : BarHandler {
    private val modes = FlightAttendantSuspensionMode.entries.associateBy { it.value }

    override suspend fun handle(context: Context, karooSystem: KarooSystemService, powerbars: List<CustomProgressBar>) {
        karooSystem.streamDataFlow(Fields.TYPE_SUSPENSION_MODE_ID)
            .map { (it as? StreamState.Streaming)?.dataPoint?.values?.get(Fields.FIELD_SUSPENSION_MODE_ID)?.toInt() }
            .distinctUntilChanged()
            .collect { modeValue ->
                powerbars.forEach { powerbar ->
                    if (modeValue != null) {
                        val mode = modes[modeValue]
                        val label = mode?.let { context.getString(it.labelResId) } ?: "?"
                        powerbar.progressColor = context.getColor(mode?.colorResId ?: R.color.zone0)
                        powerbar.progress = 0.0
                        powerbar.label = label
                        Log.d(KarooPowerbarExtension.TAG, "Suspension Mode: $label")
                    } else {
                        powerbar.progressColor = context.getColor(R.color.zone0)
                        powerbar.progress = null
                        powerbar.label = "?"
                        Log.d(KarooPowerbarExtension.TAG, "Suspension Mode: Unavailable")
                    }
                    powerbar.invalidate()
                }
            }
    }
}