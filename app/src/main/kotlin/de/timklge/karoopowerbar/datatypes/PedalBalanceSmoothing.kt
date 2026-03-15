package de.timklge.karoopowerbar

import io.hammerhead.karooext.models.DataType

enum class PedalBalanceSmoothing(val dataTypeId: String){
    RAW(DataType.Type.PEDAL_POWER_BALANCE),
    SMOOTHED_3S(DataType.Type.SMOOTHED_3S_AVERAGE_PEDAL_POWER_BALANCE),
    SMOOTHED_10S(DataType.Type.SMOOTHED_10S_AVERAGE_PEDAL_POWER_BALANCE),
    SMOOTHED_LAP(DataType.Type.AVERAGE_PEDAL_POWER_BALANCE_LAP),
    SMOOTHED_RIDE(DataType.Type.AVERAGE_PEDAL_POWER_BALANCE),
}