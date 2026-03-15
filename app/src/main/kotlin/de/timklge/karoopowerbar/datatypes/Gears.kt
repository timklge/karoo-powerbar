package de.timklge.karoopowerbar

import io.hammerhead.karooext.models.DataType

enum class Gears(val prefix: String, val dataTypeId: String, val numberFieldId: String, val maxFieldId: String) {
    FRONT("F", DataType.Type.SHIFTING_FRONT_GEAR, DataType.Field.SHIFTING_FRONT_GEAR, DataType.Field.SHIFTING_FRONT_GEAR_MAX),
    REAR("R", DataType.Type.SHIFTING_REAR_GEAR, DataType.Field.SHIFTING_REAR_GEAR, DataType.Field.SHIFTING_REAR_GEAR_MAX)
}