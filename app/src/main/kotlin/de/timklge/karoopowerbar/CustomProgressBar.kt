package de.timklge.karoopowerbar

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import kotlinx.serialization.Serializable

@Serializable
enum class CustomProgressBarSize(val id: String, val label: String, val fontSize: Float, val barHeight: Float) {
    SMALL("small", "Small", 35f, 10f),
    MEDIUM("medium", "Medium", 40f, 15f),
    LARGE("large", "Large", 60f, 25f),
}

class CustomProgressBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var progress: Double? = 0.5
    var location: PowerbarLocation = PowerbarLocation.BOTTOM
    var label: String = ""
    var minTarget: Double? = null
    var maxTarget: Double? = null
    var target: Double? = null
    var showLabel: Boolean = true
    @ColorInt var progressColor: Int = 0xFF2b86e6.toInt()

    var size = CustomProgressBarSize.MEDIUM
        set(value) {
            field = value
            textPaint.textSize = value.fontSize
            targetZoneStrokePaint.strokeWidth = when(value){
                CustomProgressBarSize.SMALL -> 3f
                CustomProgressBarSize.MEDIUM -> 6f
                CustomProgressBarSize.LARGE -> 8f
            }
            targetIndicatorPaint.strokeWidth = when(value){
                CustomProgressBarSize.SMALL -> 6f
                CustomProgressBarSize.MEDIUM -> 8f
                CustomProgressBarSize.LARGE -> 10f
            }
        }

    private val targetColor = 0xFF9933FF.toInt()

    private val targetZoneFillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = targetColor
        alpha = 100 // Semi-transparent fill
    }

    private val targetZoneStrokePaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 6f
        style = Paint.Style.STROKE
        color = targetColor
    }

    private val linePaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 1f
        style = Paint.Style.FILL_AND_STROKE
        color = progressColor
    }

    private val lineStrokePaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 4f
        style = Paint.Style.STROKE
        color = progressColor
    }

    private val blurPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 4f
        style = Paint.Style.STROKE
        color = progressColor
        maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
    }

    private val blurPaintHighlight = Paint().apply {
        isAntiAlias = true
        strokeWidth = 8f
        style = Paint.Style.FILL_AND_STROKE
        color = ColorUtils.blendARGB(progressColor, 0xFFFFFF, 0.5f)
        maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
    }

    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(1.0f, 0f, 0f, 0f)
        strokeWidth = 2f
    }

    private val textBackgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(0.8f, 0f, 0f, 0f)
        strokeWidth = 2f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 3f
        textSize = size.fontSize
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val targetIndicatorPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)

        // Determine if the current progress is within the target range
        val isTargetMet =
            progress != null && minTarget != null && maxTarget != null && progress!! >= minTarget!! && progress!! <= maxTarget!!

        linePaint.color = progressColor
        lineStrokePaint.color = progressColor
        blurPaint.color = progressColor
        blurPaintHighlight.color = ColorUtils.blendARGB(progressColor, 0xFFFFFF, 0.5f)

        when (location) {
            PowerbarLocation.TOP -> {
                val barTop = 15f
                val barBottom = barTop + size.barHeight
                val rect = RectF(
                    1f,
                    barTop,
                    ((canvas.width.toDouble() - 1f) * (progress ?: 0.0).coerceIn(
                        0.0,
                        1.0
                    )).toFloat(),
                    barBottom
                )

                canvas.drawRect(0f, barTop, canvas.width.toFloat(), barBottom, backgroundPaint)

                // Draw target zone fill behind the progress bar
                if (minTarget != null && maxTarget != null) {
                    val minTargetX = (canvas.width * minTarget!!).toFloat()
                    val maxTargetX = (canvas.width * maxTarget!!).toFloat()
                    canvas.drawRoundRect(
                        minTargetX,
                        barTop,
                        maxTargetX,
                        barBottom,
                        2f,
                        2f,
                        targetZoneFillPaint
                    )
                }

                if (progress != null) {
                    canvas.drawRoundRect(rect, 2f, 2f, blurPaint)
                    canvas.drawRoundRect(rect, 2f, 2f, linePaint)

                    canvas.drawRoundRect(
                        rect.right - 4,
                        rect.top,
                        rect.right + 4,
                        rect.bottom,
                        2f,
                        2f,
                        blurPaintHighlight
                    )

                    // Draw target zone stroke after progress bar, before label
                    if (minTarget != null && maxTarget != null) {
                        val minTargetX = (canvas.width * minTarget!!).toFloat()
                        val maxTargetX = (canvas.width * maxTarget!!).toFloat()
                        // Draw stroked rounded rectangle for the target zone
                        canvas.drawRoundRect(
                            minTargetX,
                            barTop,
                            maxTargetX,
                            barBottom,
                            2f,
                            2f,
                            targetZoneStrokePaint
                        )
                    }

                    // Draw vertical target indicator line if target is present
                    if (target != null) {
                        val targetX = (canvas.width * target!!).toFloat()
                        targetIndicatorPaint.color = if (isTargetMet) Color.GREEN else Color.RED
                        canvas.drawLine(targetX, barTop, targetX, barBottom, targetIndicatorPaint)
                    }

                    if (showLabel) {
                        val textContent =
                            label // Store original label, as textPaint.measureText can be slow
                        val measuredTextWidth = textPaint.measureText(textContent)
                        val labelBoxWidth = (measuredTextWidth + 20).coerceAtLeast(10f)
                        val labelBoxHeight = size.fontSize + 10f // Consistent height with padding

                        // Calculate horizontal position for the label box (centered around progress end, clamped)
                        val labelBoxLeft = (rect.right - labelBoxWidth / 2f).coerceIn(
                            0f,
                            canvas.width - labelBoxWidth
                        )

                        var labelBoxTop: Float
                        var labelBoxBottom: Float
                        var textYPosition: Float

                        if (target != null) { // If workout target is present, move label BELOW the bar
                            val labelPadding = 5f // Padding between bar and label box
                            labelBoxTop = barBottom + labelPadding
                            labelBoxBottom = labelBoxTop + labelBoxHeight
                            // Vertically center text in the new box
                            val labelBoxCenterY = labelBoxTop + labelBoxHeight / 2f
                            textYPosition =
                                labelBoxCenterY - (textPaint.ascent() + textPaint.descent()) / 2f
                        } else { // Original position for TOP
                            val yOffsetOriginal = when (size) {
                                CustomProgressBarSize.SMALL -> (size.fontSize - size.barHeight) / 2 + 2f
                                CustomProgressBarSize.MEDIUM, CustomProgressBarSize.LARGE -> (size.fontSize - size.barHeight) / 2
                            }
                            labelBoxTop = barTop - yOffsetOriginal
                            labelBoxBottom =
                                barBottom + yOffsetOriginal // Original calculation was based on rect.bottom which is barBottom
                            textYPosition = barBottom + 6f // Original text Y
                        }

                        lineStrokePaint.color = if (target != null){
                            if (isTargetMet) Color.GREEN else Color.RED
                        } else progressColor

                        canvas.drawRoundRect(
                            labelBoxLeft,
                            labelBoxTop,
                            labelBoxLeft + labelBoxWidth,
                            labelBoxBottom,
                            2f,
                            2f,
                            textBackgroundPaint
                        )
                        canvas.drawRoundRect(
                            labelBoxLeft,
                            labelBoxTop,
                            labelBoxLeft + labelBoxWidth,
                            labelBoxBottom,
                            2f,
                            2f,
                            blurPaint
                        )
                        canvas.drawRoundRect(
                            labelBoxLeft,
                            labelBoxTop,
                            labelBoxLeft + labelBoxWidth,
                            labelBoxBottom,
                            2f,
                            2f,
                            lineStrokePaint
                        )

                        canvas.drawText(
                            textContent,
                            labelBoxLeft + labelBoxWidth / 2f,
                            textYPosition,
                            textPaint
                        )
                    }
                }
            }

            PowerbarLocation.BOTTOM -> {
                val barTop = canvas.height.toFloat() - 1f - size.barHeight
                val barBottom = canvas.height.toFloat()
                val rect = RectF(
                    1f,
                    barTop,
                    ((canvas.width.toDouble() - 1f) * (progress ?: 0.0).coerceIn(
                        0.0,
                        1.0
                    )).toFloat(),
                    barBottom
                )

                canvas.drawRect(0f, barTop, canvas.width.toFloat(), barBottom, backgroundPaint)

                // Draw target zone fill behind the progress bar
                if (minTarget != null && maxTarget != null) {
                    val minTargetX = (canvas.width * minTarget!!).toFloat()
                    val maxTargetX = (canvas.width * maxTarget!!).toFloat()
                    canvas.drawRoundRect(
                        minTargetX,
                        barTop,
                        maxTargetX,
                        barBottom,
                        2f,
                        2f,
                        targetZoneFillPaint
                    )
                }

                if (progress != null) {
                    canvas.drawRoundRect(rect, 2f, 2f, blurPaint)
                    canvas.drawRoundRect(rect, 2f, 2f, linePaint)

                    canvas.drawRoundRect(
                        rect.right - 4,
                        rect.top,
                        rect.right + 4,
                        rect.bottom,
                        2f,
                        2f,
                        blurPaintHighlight
                    )

                    // Draw target zone stroke after progress bar, before label
                    if (minTarget != null && maxTarget != null) {
                        val minTargetX = (canvas.width * minTarget!!).toFloat()
                        val maxTargetX = (canvas.width * maxTarget!!).toFloat()
                        // Draw stroked rounded rectangle for the target zone
                        canvas.drawRoundRect(
                            minTargetX,
                            barTop,
                            maxTargetX,
                            barBottom,
                            2f,
                            2f,
                            targetZoneStrokePaint
                        )
                    }

                    // Draw vertical target indicator line if target is present
                    if (target != null) {
                        val targetX = (canvas.width * target!!).toFloat()
                        targetIndicatorPaint.color = if (isTargetMet) Color.GREEN else Color.RED
                        canvas.drawLine(targetX, barTop, targetX, barBottom, targetIndicatorPaint)
                    }

                    if (showLabel) {
                        val textContent = label // Store original label
                        val measuredTextWidth = textPaint.measureText(textContent)
                        val labelBoxWidth = (measuredTextWidth + 20).coerceAtLeast(10f)
                        val labelBoxHeight = size.fontSize + 10f // Consistent height with padding

                        // Calculate horizontal position for the label box (centered around progress end, clamped)
                        val labelBoxLeft = (rect.right - labelBoxWidth / 2f).coerceIn(
                            0f,
                            canvas.width - labelBoxWidth
                        )

                        var labelBoxTop: Float
                        var labelBoxBottom: Float
                        var textYPosition: Float

                        if (target != null) { // If workout target is present, move label ABOVE the bar
                            val labelPadding = 5f // Padding between bar and label box
                            labelBoxBottom = barTop - labelPadding
                            labelBoxTop = labelBoxBottom - labelBoxHeight
                            // Vertically center text in the new box
                            val labelBoxCenterY = labelBoxTop + labelBoxHeight / 2f
                            textYPosition =
                                labelBoxCenterY - (textPaint.ascent() + textPaint.descent()) / 2f
                        } else { // Original position for BOTTOM
                            val yOffsetOriginal = when (size) {
                                CustomProgressBarSize.SMALL -> size.fontSize / 2 + 2f
                                CustomProgressBarSize.MEDIUM -> size.fontSize / 2
                                CustomProgressBarSize.LARGE -> size.fontSize / 2 - 5f
                            }
                            labelBoxTop = barTop - yOffsetOriginal
                            labelBoxBottom = barBottom + 5f // Original 'b' calculation
                            textYPosition =
                                barBottom - 1f // Original text Y (rect.top + size.barHeight -1)
                        }

                        lineStrokePaint.color = if (target != null){
                            if (isTargetMet) Color.GREEN else Color.RED
                        } else progressColor

                        canvas.drawRoundRect(
                            labelBoxLeft,
                            labelBoxTop,
                            labelBoxLeft + labelBoxWidth,
                            labelBoxBottom,
                            2f,
                            2f,
                            textBackgroundPaint
                        )
                        canvas.drawRoundRect(
                            labelBoxLeft,
                            labelBoxTop,
                            labelBoxLeft + labelBoxWidth,
                            labelBoxBottom,
                            2f,
                            2f,
                            blurPaint
                        )
                        canvas.drawRoundRect(
                            labelBoxLeft,
                            labelBoxTop,
                            labelBoxLeft + labelBoxWidth,
                            labelBoxBottom,
                            2f,
                            2f,
                            lineStrokePaint
                        )

                        canvas.drawText(
                            textContent,
                            labelBoxLeft + labelBoxWidth / 2f,
                            textYPosition,
                            textPaint
                        )
                    }
                }
            }
        }
    }
}
