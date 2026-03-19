package de.timklge.karoopowerbar.datatypes

import io.hammerhead.karooext.models.DataType

enum class PowerStreamSmoothing(val dataTypeId: String){
    RAW(DataType.Type.POWER),
    SMOOTHED_3S(DataType.Type.SMOOTHED_3S_AVERAGE_POWER),
    SMOOTHED_10S(DataType.Type.SMOOTHED_10S_AVERAGE_POWER),
}