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

package de.timklge.karoopowerbar.datatypes

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import de.timklge.karoopowerbar.R

enum class FlightAttendantSuspensionStateValue(val value: Int, @StringRes val labelResId: Int, @ColorRes val colorResId: Int) {
    OPEN(0, R.string.flight_attendant_state_open, R.color.zone1),
    PEDAL(1, R.string.flight_attendant_state_pedal, R.color.zone3),
    LOCKED(2, R.string.flight_attendant_state_locked, R.color.zone7)
}