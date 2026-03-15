package de.timklge.karoopowerbar.datatypes

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import de.timklge.karoopowerbar.R

enum class FlightAttendantSuspensionStateValue(val value: Int, @StringRes val labelResId: Int, @ColorRes val colorResId: Int) {
    OPEN(0, R.string.flight_attendant_state_open, R.color.zone1),
    PEDAL(1, R.string.flight_attendant_state_pedal, R.color.zone3),
    LOCKED(2, R.string.flight_attendant_state_locked, R.color.zone7)
}