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

enum class FlightAttendantSuspensionLocation(val dataTypeId: String, val stateFieldId: String) {
    FRONT("TYPE_SUSPENSION_STATE_FRONT_ID", "FIELD_SUSPENSION_STATE_FRONT_ID"),
    REAR("TYPE_SUSPENSION_STATE_REAR_ID", "FIELD_SUSPENSION_STATE_REAR_ID")
}