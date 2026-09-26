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

package de.timklge.karoopowerbar

import io.hammerhead.karooext.models.UserProfile

enum class Zone(val colorResource: Int){
    Zone0(R.color.zone0),
    Zone1(R.color.zone1),
    Zone2(R.color.zone2),
    Zone3(R.color.zone3),
    Zone4(R.color.zone4),
    Zone5(R.color.zone5),
    Zone6(R.color.zone6),
    Zone7(R.color.zone7),
    Zone8(R.color.zone8),
}

val zones = mapOf(
    1 to listOf(Zone.Zone7),
    2 to listOf(Zone.Zone1, Zone.Zone7),
    3 to listOf(Zone.Zone1, Zone.Zone3, Zone.Zone7),
    4 to listOf(Zone.Zone1, Zone.Zone3, Zone.Zone5, Zone.Zone7),
    5 to listOf(Zone.Zone1, Zone.Zone2, Zone.Zone3, Zone.Zone5, Zone.Zone7),
    6 to listOf(Zone.Zone1, Zone.Zone2, Zone.Zone3, Zone.Zone5, Zone.Zone7, Zone.Zone8),
    7 to listOf(Zone.Zone1, Zone.Zone2, Zone.Zone3, Zone.Zone5, Zone.Zone6, Zone.Zone7, Zone.Zone8),
    8 to listOf(Zone.Zone0, Zone.Zone1, Zone.Zone2, Zone.Zone3, Zone.Zone5, Zone.Zone6, Zone.Zone7, Zone.Zone8),
    9 to listOf(Zone.Zone0, Zone.Zone1, Zone.Zone2, Zone.Zone3, Zone.Zone4, Zone.Zone5, Zone.Zone6, Zone.Zone7, Zone.Zone8)
)

fun getZone(userZones: List<UserProfile.Zone>, value: Int): Zone? {
    val zoneList = zones[userZones.size] ?: return null

    userZones.forEachIndexed { index, zone ->
        if (value in zone.min..zone.max) {
            return zoneList.getOrNull(index) ?: Zone.Zone7
        }
    }

    return null
}

val zoneList = listOf(Zone.Zone0, Zone.Zone1, Zone.Zone2, Zone.Zone3, Zone.Zone4, Zone.Zone5, Zone.Zone6, Zone.Zone7, Zone.Zone8)

fun getZone(progress: Double): Zone {
    val index = (progress * zoneList.size).toInt().coerceIn(0, zoneList.size - 1)
    return zoneList[index]
}