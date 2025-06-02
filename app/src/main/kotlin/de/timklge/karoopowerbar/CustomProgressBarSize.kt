package de.timklge.karoopowerbar

import kotlinx.serialization.Serializable

@Serializable
enum class CustomProgressBarSize(val id: String, val label: String, val fontSize: Float, val barHeight: Float) {
    SMALL("small", "Small", 35f, 10f),
    MEDIUM("medium", "Medium", 40f, 15f),
    LARGE("large", "Large", 60f, 25f),
}

@Serializable
enum class CustomProgressBarFontSize(val id: String, val label: String, val fontSize: Float) {
    SMALL("small", "Small", 35f),
    MEDIUM("medium", "Medium", 40f),
    LARGE("large", "Large", 60f);

    companion object {
        fun fromSize(size: CustomProgressBarSize): CustomProgressBarFontSize {
            return when (size) {
                CustomProgressBarSize.SMALL -> SMALL
                CustomProgressBarSize.MEDIUM -> MEDIUM
                CustomProgressBarSize.LARGE -> LARGE
            }
        }
    }
}

@Serializable
enum class CustomProgressBarBarSize(val id: String, val label: String, val barHeight: Float) {
    NONE("none", "None", 0f),
    SMALL("small", "Small", 10f),
    MEDIUM("medium", "Medium", 15f),
    LARGE("large", "Large", 25f);

    companion object {
        fun fromSize(size: CustomProgressBarSize): CustomProgressBarBarSize {
            return when (size) {
                CustomProgressBarSize.SMALL -> SMALL
                CustomProgressBarSize.MEDIUM -> MEDIUM
                CustomProgressBarSize.LARGE -> LARGE
            }
        }
    }
}
