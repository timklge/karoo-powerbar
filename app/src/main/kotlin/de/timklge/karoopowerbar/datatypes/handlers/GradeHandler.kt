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
import de.timklge.karoopowerbar.datatypes.BarHandler
import de.timklge.karoopowerbar.remap
import de.timklge.karoopowerbar.streamDataFlow
import de.timklge.karoopowerbar.streamSettings
import de.timklge.karoopowerbar.throttle
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale
import kotlin.math.absoluteValue

class GradeHandler : BarHandler {
    override suspend fun handle(context: Context, karooSystem: KarooSystemService, powerbars: List<CustomProgressBar>) {
        @ColorRes
        fun getInclineIndicatorColor(percent: Float): Int? = when (percent) {
            in -Float.MAX_VALUE..<-7.5f -> R.color.eleDarkBlue
            in -7.5f..<-4.6f -> R.color.eleLightBlue
            in -4.6f..<-2f -> R.color.eleWhite
            in -2f..<2f -> R.color.eleGray
            in 2f..<4.6f -> R.color.eleDarkGreen
            in 4.6f..<7.5f -> R.color.eleLightGreen
            in 7.5f..<12.5f -> R.color.eleYellow
            in 12.5f..<15.5f -> R.color.eleLightOrange
            in 15.5f..<19.5f -> R.color.eleDarkOrange
            in 19.5f..<23.5f -> R.color.eleRed
            in 23.5f..Float.MAX_VALUE -> R.color.elePurple
            else -> null
        }

        val gradeFlow = karooSystem.streamDataFlow(DataType.Type.ELEVATION_GRADE)
            .map { (it as? StreamState.Streaming)?.dataPoint?.singleValue }
            .distinctUntilChanged()

        data class StreamData(val value: Double?, val settings: PowerbarSettings?)
        combine(gradeFlow, context.streamSettings()) { grade, settings ->
            StreamData(
                grade,
                settings
            )
        }
            .distinctUntilChanged().throttle(1_000).collect { streamData ->
                val value = streamData.value
                powerbars.forEach { powerbar ->
                    if (value != null) {
                        val minGradient = streamData.settings?.minGradient ?: PowerbarSettings.defaultMinGradient
                        val maxGradient = streamData.settings?.maxGradient ?: PowerbarSettings.defaultMaxGradient
                        val useAbsoluteValue = minGradient >= 0

                        powerbar.progress = remap(
                            if (useAbsoluteValue) value.absoluteValue else value,
                            minGradient.toDouble(),
                            maxGradient.toDouble(),
                            0.0,
                            1.0
                        )
                        powerbar.progressColor = context.getColor(getInclineIndicatorColor(value.toFloat()) ?: R.color.zone0)
                        powerbar.label = "${String.format(Locale.getDefault(), "%.1f", value)}%"
                        Log.d(KarooPowerbarExtension.TAG, "Grade: $value")
                    } else {
                        powerbar.progressColor = context.getColor(R.color.zone0)
                        powerbar.progress = null
                        powerbar.label = "?"
                        Log.d(KarooPowerbarExtension.TAG, "Grade: Unavailable")
                    }
                    powerbar.invalidate()
                }
            }
    }
}