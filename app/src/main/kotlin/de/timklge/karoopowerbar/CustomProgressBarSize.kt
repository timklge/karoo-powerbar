package de.timklge.karoopowerbar

import kotlinx.serialization.Serializable

@Serializable
enum class CustomProgressBarSize(val id: String, val labelResId: Int, val fontSize: Float, val barHeight: Float) {
    SMALL("small", R.string.size_small, 35f, 10f),
    MEDIUM("medium", R.string.size_medium, 40f, 15f),
    LARGE("large", R.string.size_large, 60f, 25f),
}

@Serializable
enum class CustomProgressBarFontSize(val id: String, val labelResId: Int, val fontSize: Float) {
    SMALL("small", R.string.size_small, 35f),
    MEDIUM("medium", R.string.size_medium, 40f),
    LARGE("large", R.string.size_large, 60f);

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
enum class CustomProgressBarBarSize(val id: String, val labelResId: Int, val barHeight: Float) {
    NONE("none", R.string.size_none, 0f),
    SMALL("small", R.string.size_small, 10f),
    MEDIUM("medium", R.string.size_medium, 15f),
    LARGE("large", R.string.size_large, 25f);

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
