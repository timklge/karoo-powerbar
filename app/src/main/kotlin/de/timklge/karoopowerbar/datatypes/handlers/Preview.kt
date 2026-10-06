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

import de.timklge.karoopowerbar.CustomProgressBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal suspend fun <T> Flow<T>.collectPreview(
    powerbars: List<CustomProgressBar>,
    update: (T, Double, CustomProgressBar) -> Unit
) {
    collectLatest { settings ->
        while (true) {
            val randomValue = Random.nextDouble()
            powerbars.forEach { powerbar ->
                update(settings, randomValue, powerbar)
                powerbar.invalidate()
            }
            delay(2.seconds)
        }
    }
}

internal fun interpolate(min: Double, max: Double, fraction: Double): Double =
    min + (max - min) * fraction
