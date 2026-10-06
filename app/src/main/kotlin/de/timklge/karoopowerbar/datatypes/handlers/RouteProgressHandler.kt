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
import de.timklge.karoopowerbar.remap
import de.timklge.karoopowerbar.streamDataFlow
import de.timklge.karoopowerbar.streamNavigationState
import de.timklge.karoopowerbar.streamUserProfile
import de.timklge.karoopowerbar.throttle
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

class RouteProgressHandler : BarHandler by RouteDistanceHandler({ riddenDistance, _ -> riddenDistance })

class RemainingRouteHandler : BarHandler by RouteDistanceHandler({ _, distanceToDestination -> distanceToDestination })

private class RouteDistanceHandler(
    private val distanceForLabel: (riddenDistance: Double?, distanceToDestination: Double?) -> Double?
) : BarHandler {
    private data class BarProgress(val progress: Double?, val label: String?)

    override suspend fun preview(
        context: Context,
        karooSystem: KarooSystemService,
        powerbars: List<CustomProgressBar>
    ) {
        karooSystem.streamUserProfile().collectPreview(powerbars) { userProfile, fraction, powerbar ->
            val totalDistance = 60_000.0
            val riddenDistance = totalDistance * fraction
            val distanceToDestination = totalDistance - riddenDistance
            val barProgress = getProgress(userProfile, riddenDistance, distanceToDestination)

            powerbar.progressColor = context.getColor(R.color.zone0)
            powerbar.progress = barProgress.progress
            powerbar.label = barProgress.label ?: ""
        }
    }

    private fun getProgress(userProfile: UserProfile, riddenDistance: Double?, distanceToDestination: Double?): BarProgress {
        val progress = if (distanceToDestination != null && riddenDistance != null) {
            remap(riddenDistance, 0.0, riddenDistance + distanceToDestination, 0.0, 1.0)
        } else {
            null
        }
        val distance = distanceForLabel(riddenDistance, distanceToDestination)
        val distanceInUserUnit = when (userProfile.preferredUnit.distance) {
            UserProfile.PreferredUnit.UnitType.IMPERIAL -> distance?.times(0.000621371)?.roundToInt()
            else -> distance?.times(0.001)?.roundToInt()
        }
        return BarProgress(progress, distanceInUserUnit?.toString())
    }

    override suspend fun handle(context: Context, karooSystem: KarooSystemService, powerbars: List<CustomProgressBar>) {
        data class StreamData(
            val userProfile: UserProfile,
            val distanceToDestination: Double?,
            val navigationState: OnNavigationState,
            val riddenDistance: Double?
        )

        combine(
            karooSystem.streamUserProfile(),
            karooSystem.streamDataFlow(DataType.Type.DISTANCE_TO_DESTINATION),
            karooSystem.streamNavigationState(),
            karooSystem.streamDataFlow(DataType.Type.DISTANCE)
        ) { userProfile, distanceToDestination, navigationState, riddenDistance ->
            StreamData(
                userProfile,
                (distanceToDestination as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.DISTANCE_TO_DESTINATION),
                navigationState,
                (riddenDistance as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.DISTANCE)
            )
        }.distinctUntilChanged().throttle(5_000).collect { streamData ->
            val barProgress = getProgress(streamData.userProfile, streamData.riddenDistance, streamData.distanceToDestination)
            powerbars.forEach { powerbar ->
                powerbar.progressColor = context.getColor(R.color.zone0)
                powerbar.progress = barProgress.progress
                powerbar.label = barProgress.label ?: ""
                powerbar.invalidate()
            }
        }
    }
}