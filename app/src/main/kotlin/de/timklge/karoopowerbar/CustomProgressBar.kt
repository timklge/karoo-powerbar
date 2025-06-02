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

class CustomProgressBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var progress: Double? = 0.5
    var location: PowerbarLocation = PowerbarLocation.BOTTOM
    var label: String = ""
    var showLabel: Boolean = true
    var barBackground: Boolean = false
    @ColorInt var progressColor: Int = 0xFF2b86e6.toInt()

    var fontSize = CustomProgressBarFontSize.MEDIUM
        set(value) {
            field = value
            textPaint.textSize = value.fontSize
            invalidate() // Redraw to apply new font size
        }

    var barSize = CustomProgressBarBarSize.MEDIUM
        set(value) {
            field = value
            invalidate() // Redraw to apply new bar size
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
        textSize = fontSize.fontSize
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        textAlign = Paint.Align.CENTER
    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)

        linePaint.color = progressColor
        lineStrokePaint.color = progressColor
        blurPaint.color = progressColor
        blurPaintHighlight.color = ColorUtils.blendARGB(progressColor, 0xFFFFFF, 0.5f)

        when(location){
            PowerbarLocation.TOP -> {
                val rect = RectF(
                    1f,
                    15f,
                    ((canvas.width.toDouble() - 1f) * (progress ?: 0.0).coerceIn(0.0, 1.0)).toFloat(),
                    15f + barSize.barHeight // barSize.barHeight will be 0f if NONE
                )

                // Draw bar components only if barSize is not NONE
                if (barSize != CustomProgressBarBarSize.NONE) {
                    if (barBackground){
                        canvas.drawRect(0f, 15f, canvas.width.toFloat(), 15f + barSize.barHeight, backgroundPaint)
                    }

                    if (progress != null) {
                        canvas.drawRoundRect(rect, 2f, 2f, blurPaint)
                        canvas.drawRoundRect(rect, 2f, 2f, linePaint)
                        canvas.drawRoundRect(rect.right-4, rect.top, rect.right+4, rect.bottom, 2f, 2f, blurPaintHighlight)
                    }
                }

                // Draw label (if progress is not null and showLabel is true)
                if (progress != null) {
                    if (showLabel){
                        val textBounds = textPaint.measureText(label)
                        val xOffset = (textBounds + 20).coerceAtLeast(10f) / 2f
                        val x = (rect.right - xOffset).coerceIn(0f..canvas.width-xOffset*2f)
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
                    1f,
                    canvas.height.toFloat() - 1f - barSize.barHeight, // barSize.barHeight will be 0f if NONE
                    ((canvas.width.toDouble() - 1f) * (progress ?: 0.0).coerceIn(0.0, 1.0)).toFloat(),
                    canvas.height.toFloat()
                )

                // Draw bar components only if barSize is not NONE
                if (barSize != CustomProgressBarBarSize.NONE) {
                    if (barBackground){
                        // Use barSize.barHeight for background top calculation
                        canvas.drawRect(0f, canvas.height.toFloat() - barSize.barHeight, canvas.width.toFloat(), canvas.height.toFloat(), backgroundPaint)
                    }

                    if (progress != null) {
                        canvas.drawRoundRect(rect, 2f, 2f, blurPaint)
                        canvas.drawRoundRect(rect, 2f, 2f, linePaint)
                        canvas.drawRoundRect(rect.right-4, rect.top, rect.right+4, rect.bottom, 2f, 2f, blurPaintHighlight)
                    }
                }

                // Draw label (if progress is not null and showLabel is true)
                if (progress != null) {
                    if (showLabel){
                        val textBounds = textPaint.measureText(label)
                        val xOffset = (textBounds + 20).coerceAtLeast(10f) / 2f
                        val x = (rect.right - xOffset).coerceIn(0f..canvas.width-xOffset*2f)
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
}
