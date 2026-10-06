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
import de.timklge.karoopowerbar.CustomProgressBar
import de.timklge.karoopowerbar.R
import de.timklge.karoopowerbar.datatypes.BarHandler
import de.timklge.karoopowerbar.streamDataFlow
import de.timklge.karoopowerbar.streamUserProfile
import de.timklge.karoopowerbar.throttle
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

class AscentHandler : BarHandler by AscentDataHandler(false)

class RemainingAscentHandler : BarHandler by AscentDataHandler(true)

private class AscentDataHandler(private val showRemainingAscent: Boolean) : BarHandler {
    private data class StreamData(
        val currentAscent: Double?,
        val ascentRemaining: Double?,
        val userProfile: UserProfile
    )

    override suspend fun preview(
        context: Context,
        karooSystem: KarooSystemService,
        powerbars: List<CustomProgressBar>
    ) {
        karooSystem.streamUserProfile().collectPreview(powerbars) { userProfile, fraction, powerbar ->
            val totalAscent = 1_000.0
            val currentAscent = totalAscent * fraction
            val ascentRemaining = totalAscent - currentAscent
            val elevationMultiplier =
                if (userProfile.preferredUnit.elevation == UserProfile.PreferredUnit.UnitType.IMPERIAL) {
                    3.28084
                } else {
                    1.0
                }
            val ascentForLabel = if (showRemainingAscent) ascentRemaining else currentAscent

            powerbar.progressColor = context.getColor(R.color.zone0)
            powerbar.progress = fraction
            powerbar.label = ascentForLabel.times(elevationMultiplier).roundToInt().toString()
        }
    }

    override suspend fun handle(context: Context, karooSystem: KarooSystemService, powerbars: List<CustomProgressBar>) {
        combine(
            karooSystem.streamDataFlow(DataType.Type.ELEVATION_GAIN),
            karooSystem.streamDataFlow(DataType.Type.ELEVATION_REMAINING),
            karooSystem.streamUserProfile()
        ) { elevationGain, elevationRemaining, userProfile ->
            StreamData(
                currentAscent = (elevationGain as? StreamState.Streaming)?.dataPoint?.singleValue,
                ascentRemaining = (elevationRemaining as? StreamState.Streaming)
                    ?.dataPoint?.values?.get(DataType.Field.ASCENT_REMAINING),
                userProfile = userProfile
            )
        }.distinctUntilChanged().throttle(5_000).collect { streamData ->
            val currentAscent = streamData.currentAscent
            val ascentRemaining = streamData.ascentRemaining
            val progress = if (currentAscent != null && ascentRemaining != null) {
                val totalAscent = currentAscent + ascentRemaining
                if (totalAscent > 0.0) {
                    (currentAscent / totalAscent).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
            } else {
                null
            }

            val elevationMultiplier =
                if (streamData.userProfile.preferredUnit.elevation == UserProfile.PreferredUnit.UnitType.IMPERIAL) {
                    3.28084
                } else {
                    1.0
                }
            val ascentForLabel = if (showRemainingAscent) ascentRemaining else currentAscent
            val label = ascentForLabel?.times(elevationMultiplier)?.roundToInt()?.toString() ?: "?"

            powerbars.forEach { powerbar ->
                powerbar.progressColor = context.getColor(R.color.zone0)
                powerbar.progress = progress
                powerbar.label = label
                powerbar.invalidate()
            }
        }
    }
}
