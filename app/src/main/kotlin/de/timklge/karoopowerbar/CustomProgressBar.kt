package de.timklge.karoopowerbar

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import de.timklge.karoopowerbar.screens.SelectedSource

class CustomView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var progressBars: Map<HorizontalPowerbarLocation, CustomProgressBar>? = null

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)

        // Draw all progress bars
        progressBars?.values?.forEach { progressBar ->
            Log.d(KarooPowerbarExtension.TAG, "Drawing progress bar for source: ${progressBar.source} - location: ${progressBar.location} - horizontalLocation: ${progressBar.horizontalLocation}")
            progressBar.onDrawForeground(canvas)
        }
    }
}

class CustomProgressBar(private val view: CustomView,
                        val source: SelectedSource,
                        val location: PowerbarLocation,
                        val horizontalLocation: HorizontalPowerbarLocation) {
    var progress: Double? = 0.5
    var label: String = ""
    var minTarget: Double? = null
    var maxTarget: Double? = null
    var target: Double? = null
    var showLabel: Boolean = true
    var barBackground: Boolean = false
    @ColorInt var progressColor: Int = 0xFF2b86e6.toInt()

    var fontSize = CustomProgressBarFontSize.MEDIUM
        set(value) {
            field = value
            textPaint.textSize = value.fontSize
            view.invalidate() // Redraw to apply new font size
        }

    var barSize = CustomProgressBarBarSize.MEDIUM
        set(value) {
            field = value
            targetZoneStrokePaint.strokeWidth = when(value){
                CustomProgressBarBarSize.NONE, CustomProgressBarBarSize.SMALL -> 3f
                CustomProgressBarBarSize.MEDIUM -> 6f
                CustomProgressBarBarSize.LARGE -> 8f
            }
            targetIndicatorPaint.strokeWidth = when(value){
                CustomProgressBarBarSize.NONE, CustomProgressBarBarSize.SMALL -> 6f
                CustomProgressBarBarSize.MEDIUM -> 8f
                CustomProgressBarBarSize.LARGE -> 10f
            }
            view.invalidate() // Redraw to apply new bar size
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
        strokeWidth = 6f
        style = Paint.Style.STROKE
        color = progressColor
        maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
    }

    private val blurPaintHighlight = Paint().apply {
        isAntiAlias = true
        strokeWidth = 10f
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
        textSize = fontSize.fontSize
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val targetIndicatorPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }

    fun onDrawForeground(canvas: Canvas) {
        // Determine if the current progress is within the target range
        val isTargetMet =
            progress != null && minTarget != null && maxTarget != null && progress!! >= minTarget!! && progress!! <= maxTarget!!

        linePaint.color = progressColor
        lineStrokePaint.color = progressColor
        blurPaint.color = progressColor
        blurPaintHighlight.color = ColorUtils.blendARGB(progressColor, 0xFFFFFF, 0.5f)

        val p = (progress ?: 0.0).coerceIn(0.0, 1.0)
        val fullWidth = canvas.width.toFloat()
        val halfWidth = fullWidth / 2f

        val barLeft = when (horizontalLocation) {
            HorizontalPowerbarLocation.LEFT -> 0f
            HorizontalPowerbarLocation.RIGHT -> fullWidth - (halfWidth * p).toFloat()
            HorizontalPowerbarLocation.FULL -> 0f
        }
        val barRight = when (horizontalLocation) {
            HorizontalPowerbarLocation.LEFT -> (halfWidth * p).toFloat()
            HorizontalPowerbarLocation.RIGHT -> fullWidth
            HorizontalPowerbarLocation.FULL -> (fullWidth * p).toFloat()
        }

        val minTargetX = when (horizontalLocation) {
            HorizontalPowerbarLocation.LEFT -> if (minTarget != null) (halfWidth * minTarget!!).toFloat() else 0f
            HorizontalPowerbarLocation.RIGHT -> if (minTarget != null) halfWidth + (halfWidth * minTarget!!).toFloat() else 0f
            HorizontalPowerbarLocation.FULL -> if (minTarget != null) (fullWidth * minTarget!!).toFloat() else 0f
        }
        val maxTargetX = when (horizontalLocation) {
            HorizontalPowerbarLocation.LEFT -> if (maxTarget != null) (halfWidth * maxTarget!!).toFloat() else 0f
            HorizontalPowerbarLocation.RIGHT -> if (maxTarget != null) halfWidth + (halfWidth * maxTarget!!).toFloat() else 0f
            HorizontalPowerbarLocation.FULL -> if (maxTarget != null) (fullWidth * maxTarget!!).toFloat() else 0f
        }
        val targetX = when (horizontalLocation) {
            HorizontalPowerbarLocation.LEFT -> if (target != null) (halfWidth * target!!).toFloat() else 0f
            HorizontalPowerbarLocation.RIGHT -> if (target != null) halfWidth + (halfWidth * target!!).toFloat() else 0f
            HorizontalPowerbarLocation.FULL -> if (target != null) (fullWidth * target!!).toFloat() else 0f
        }

        val backgroundLeft = when (horizontalLocation) {
            HorizontalPowerbarLocation.LEFT -> 0f
            HorizontalPowerbarLocation.RIGHT -> halfWidth
            HorizontalPowerbarLocation.FULL -> 0f
        }
        val backgroundRight = when (horizontalLocation) {
            HorizontalPowerbarLocation.LEFT -> halfWidth
            HorizontalPowerbarLocation.RIGHT -> fullWidth
            HorizontalPowerbarLocation.FULL -> fullWidth
        }

        when (location) {
            PowerbarLocation.TOP -> {
                val rect = RectF(
                    barLeft,
                    15f,
                    barRight,
                    15f + barSize.barHeight // barSize.barHeight will be 0f if NONE
                )

                // Draw bar components only if barSize is not NONE
                if (barSize != CustomProgressBarBarSize.NONE) {
                    if (barBackground){
                        canvas.drawRect(backgroundLeft, 15f, backgroundRight, 15f + barSize.barHeight, backgroundPaint)
                    }

                    // Draw target zone fill behind the progress bar
                    if (minTarget != null && maxTarget != null) {
                        canvas.drawRoundRect(
                            minTargetX,
                            15f,
                            maxTargetX,
                            15f + barSize.barHeight,
                            2f,
                            2f,
                            targetZoneFillPaint
                        )
                    }

                    if (progress != null) {
                        canvas.drawRoundRect(rect, 2f, 2f, blurPaint)
                        canvas.drawRoundRect(rect, 2f, 2f, linePaint)
                        canvas.drawRoundRect(rect.right-4, rect.top, rect.right+4, rect.bottom, 2f, 2f, blurPaintHighlight)
                    }
                }
                // Draw label (if progress is not null and showLabel is true)
                if (progress != null) {
                    // Draw target zone stroke after progress bar, before label
                    if (minTarget != null && maxTarget != null) {
                        // Draw stroked rounded rectangle for the target zone
                        canvas.drawRoundRect(
                            minTargetX,
                            15f,
                            maxTargetX,
                            15f + barSize.barHeight,
                            2f,
                            2f,
                            targetZoneStrokePaint
                        )
                    }

                    // Draw vertical target indicator line if target is present
                    if (target != null) {
                        targetIndicatorPaint.color = if (isTargetMet) Color.GREEN else Color.RED
                        canvas.drawLine(targetX, 15f, targetX, 15f + barSize.barHeight, targetIndicatorPaint)
                    }

                    if (showLabel){
                        lineStrokePaint.color = if (target != null){
                            if (isTargetMet) Color.GREEN else Color.RED
                        } else progressColor

                        blurPaint.color = lineStrokePaint.color
                        blurPaintHighlight.color = ColorUtils.blendARGB(lineStrokePaint.color, 0xFFFFFF, 0.5f)

                        val textBounds = textPaint.measureText(label)
                        val xOffset = (textBounds + 20).coerceAtLeast(10f) / 2f
                        val x = (if (horizontalLocation != HorizontalPowerbarLocation.RIGHT) rect.right - xOffset else rect.left - xOffset).coerceIn(backgroundLeft..backgroundRight-xOffset*2f)
                        val r = x + xOffset * 2

                        val fm = textPaint.fontMetrics
                        // barCenterY calculation uses barSize.barHeight, which is 0f for NONE,
                        // correctly centering the label on the 15f line.
                        val barCenterY = rect.top + barSize.barHeight / 2f
                        val centeredTextBaselineY = barCenterY - (fm.ascent + fm.descent) / 2f
                        val calculatedTextBoxTop = centeredTextBaselineY + fm.ascent
                        val finalTextBoxTop = calculatedTextBoxTop.coerceAtLeast(0f)
                        val finalTextBaselineY = finalTextBoxTop - fm.ascent
                        val finalTextBoxBottom = finalTextBaselineY + fm.descent

                        canvas.drawRoundRect(x, finalTextBoxTop, r, finalTextBoxBottom, 2f, 2f, textBackgroundPaint)
                        canvas.drawRoundRect(x, finalTextBoxTop, r, finalTextBoxBottom, 2f, 2f, blurPaint)
                        canvas.drawRoundRect(x, finalTextBoxTop, r, finalTextBoxBottom, 2f, 2f, lineStrokePaint)
                        canvas.drawText(label, x + xOffset, finalTextBaselineY, textPaint)
                        }
                    }
            }

            PowerbarLocation.BOTTOM -> {
                val rect = RectF(
                    barLeft,
                    canvas.height.toFloat() - 1f - barSize.barHeight, // barSize.barHeight will be 0f if NONE
                    barRight,
                    canvas.height.toFloat()
                )

                // Draw bar components only if barSize is not NONE
                if (barSize != CustomProgressBarBarSize.NONE) {
                    if (barBackground){
                        // Use barSize.barHeight for background top calculation
                        canvas.drawRect(backgroundLeft, canvas.height.toFloat() - barSize.barHeight, backgroundRight, canvas.height.toFloat(), backgroundPaint)
                    }

                    // Draw target zone fill behind the progress bar
                    if (minTarget != null && maxTarget != null) {
                        canvas.drawRoundRect(
                            minTargetX,
                            canvas.height.toFloat() - barSize.barHeight,
                            maxTargetX,
                            canvas.height.toFloat(),
                            2f,
                            2f,
                            targetZoneFillPaint
                        )
                    }

                    if (progress != null) {
                        canvas.drawRoundRect(rect, 2f, 2f, blurPaint)
                        canvas.drawRoundRect(rect, 2f, 2f, linePaint)
                        canvas.drawRoundRect(rect.right-4, rect.top, rect.right+4, rect.bottom, 2f, 2f, blurPaintHighlight)
                    }
                }

                // Draw label (if progress is not null and showLabel is true)
                if (progress != null) {
                    // Draw target zone stroke after progress bar, before label
                    if (minTarget != null && maxTarget != null) {
                        // Draw stroked rounded rectangle for the target zone
                        canvas.drawRoundRect(
                            minTargetX,
                            canvas.height.toFloat() - barSize.barHeight,
                            maxTargetX,
                            canvas.height.toFloat(),
                            2f,
                            2f,
                            targetZoneStrokePaint
                        )
                    }

                    // Draw vertical target indicator line if target is present
                    if (target != null) {
                        targetIndicatorPaint.color = if (isTargetMet) Color.GREEN else Color.RED
                        canvas.drawLine(targetX, canvas.height.toFloat() - barSize.barHeight, targetX, canvas.height.toFloat(), targetIndicatorPaint)
                    }

                    if (showLabel){
                        lineStrokePaint.color = if (target != null){
                            if (isTargetMet) Color.GREEN else Color.RED
                        } else progressColor

                        blurPaint.color = lineStrokePaint.color
                        blurPaintHighlight.color = ColorUtils.blendARGB(lineStrokePaint.color, 0xFFFFFF, 0.5f)

                        val textBounds = textPaint.measureText(label)
                        val xOffset = (textBounds + 20).coerceAtLeast(10f) / 2f
                        val x = (if (horizontalLocation != HorizontalPowerbarLocation.RIGHT) rect.right - xOffset else rect.left - xOffset).coerceIn(backgroundLeft..backgroundRight-xOffset*2f)
                        val r = x + xOffset * 2

                        // textDrawBaselineY calculation uses rect.top and barSize.barHeight.
                        // If NONE, barSize.barHeight is 0f. rect.top becomes canvas.height - 1f.
                        // So, baseline is (canvas.height - 1f) + 0f - 1f = canvas.height - 2f.
                        val textDrawBaselineY = rect.top + barSize.barHeight - 1f
                        val yBox = textDrawBaselineY + textPaint.ascent()
                        val bBox = textDrawBaselineY + textPaint.descent()

                        canvas.drawRoundRect(x, yBox, r, bBox, 2f, 2f, textBackgroundPaint)
                        canvas.drawRoundRect(x, yBox, r, bBox, 2f, 2f, blurPaint)
                        canvas.drawRoundRect(x, yBox, r, bBox, 2f, 2f, lineStrokePaint)
                        canvas.drawText(label, x + xOffset, textDrawBaselineY, textPaint)
                    }
                }
            }
        }
    }

    fun invalidate() {
        // Invalidate the view to trigger a redraw
        view.invalidate()
    }
}
