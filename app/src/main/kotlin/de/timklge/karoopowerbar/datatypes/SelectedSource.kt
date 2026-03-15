package de.timklge.karoopowerbar.screens

import de.timklge.karoopowerbar.R

enum class SelectedSource(val id: String, val labelResId: Int) {
    NONE("none", R.string.source_none),
    HEART_RATE("hr", R.string.source_heart_rate),
    POWER("power", R.string.source_power),
    POWER_3S("power_3s", R.string.source_power_3s),
    POWER_10S("power_10s", R.string.source_power_10s),
    SPEED("speed", R.string.source_speed),
    SPEED_3S("speed_3s", R.string.source_speed_3s),
    CADENCE("cadence", R.string.source_cadence),
    CADENCE_3S("cadence_3s", R.string.source_cadence_3s),
    GRADE("grade", R.string.source_grade),
    POWER_BALANCE("power_balance", R.string.source_power_balance),
    POWER_BALANCE_3S("power_balance_3s", R.string.source_power_balance_3s),
    POWER_BALANCE_10S("power_balance_10s", R.string.source_power_balance_10s),
    POWER_BALANCE_LAP("power_balance_lap", R.string.source_power_balance_lap),
    POWER_BALANCE_AVG("power_balance_avg", R.string.source_power_balance_avg),
    ROUTE_PROGRESS("route_progress", R.string.source_route_progress),
    REMAINING_ROUTE("route_progress_remaining", R.string.source_route_remaining),
    FRONT_GEAR("front_gear", R.string.source_front_gear),
    REAR_GEAR("rear_gear", R.string.source_rear_gear),
    FLIGHT_ATTENDANT_SUSPENSION_STATE_REAR("flight_attendant_suspension_state_rear", R.string.source_flight_attendant_suspension_state_rear),
    FLIGHT_ATTENDANT_SUSPENSION_STATE_FRONT("flight_attendant_suspension_state_front", R.string.source_flight_attendant_suspension_state_front),
    FLIGHT_ATTENDANT_SUSPENSION_MODE("flight_attendant_suspension_mode", R.string.source_flight_attendant_suspension_mode);

    fun isPower() = this == POWER || this == POWER_3S || this == POWER_10S
}