package de.timklge.karoopowerbar

import androidx.annotation.ColorRes
import androidx.annotation.StringRes

enum class FlightAttendantSuspensionMode(val value: Int, @StringRes val labelResId: Int, @ColorRes val colorResId: Int) {
    STARTUP(0, R.string.flight_attendant_mode_startup, R.color.zone0),
    AUTOMATIC(1, R.string.flight_attendant_mode_automatic, R.color.zone1),
    OVERRIDE(2, R.string.flight_attendant_mode_override, R.color.zone5),
    SAFETY(3, R.string.flight_attendant_mode_safety, R.color.zone6),
    MANUAL(4, R.string.flight_attendant_mode_manual, R.color.zone7),
    BIAS(5, R.string.flight_attendant_mode_bias, R.color.zone4),
    FORK_LSC(6, R.string.flight_attendant_mode_fork_lsc, R.color.zone2),
    RS_LSC(7, R.string.flight_attendant_mode_rs_lsc, R.color.zone3),
    CALIBRATING(8, R.string.flight_attendant_mode_calibrating, R.color.zone0)
}