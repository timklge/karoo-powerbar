package de.timklge.karoopowerbar.datatypes

enum class FlightAttendantSuspensionLocation(val dataTypeId: String, val stateFieldId: String) {
    FRONT("TYPE_SUSPENSION_STATE_FRONT_ID", "FIELD_SUSPENSION_STATE_FRONT_ID"),
    REAR("TYPE_SUSPENSION_STATE_REAR_ID", "FIELD_SUSPENSION_STATE_REAR_ID")
}