package de.timklge.karoopowerbar

import io.hammerhead.karooext.models.UserProfile

enum class PowerZone(val colorResource: Int) {
    ACTIVE_RECOVERY(R.color.zoneActiveRecovery),
    ENDURANCE(R.color.zoneEndurance),
    TEMPO(R.color.zoneTempo),
    THRESHOLD(R.color.zoneThreshold),
    VO2_MAX(R.color.zoneVO2Max),
    AEROBIC_CAPACITY(R.color.zoneAerobic),
    ANAEROBIC_CAPACITY(R.color.zoneAnaerobic),
}

enum class HrZone(val colorResource: Int) {
    ACTIVE_RECOVERY(R.color.zoneActiveRecovery),
    ENDURANCE(R.color.zoneEndurance),
    TEMPO(R.color.zoneTempo),
    THRESHOLD(R.color.zoneThreshold),
    VO2_MAX(R.color.zoneAerobic),
}

fun UserProfile.getUserPowerZone(power: Int): PowerZone? {
    powerZones.forEachIndexed { index, zone ->
        if (power in zone.min..zone.max) {
            return PowerZone.entries[index]
        }
    }

    return null
}

fun UserProfile.getUserHrZone(hr: Int): HrZone? {
    heartRateZones.forEachIndexed { index, zone ->
        if (hr in zone.min..zone.max) {
            return HrZone.entries[index]
        }
    }

    return null
}