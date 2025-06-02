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
    var showLabel: Boolean = true
    var barBackground: Boolean = false
    @ColorInt var progressColor: Int = 0xFF2b86e6.toInt()

    var size = CustomProgressBarSize.MEDIUM
        set(value) {
            field = value
            textPaint.textSize = value.fontSize
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
                    15f + size.barHeight
                )

                if (barBackground){
                    canvas.drawRect(0f, 15f, canvas.width.toFloat(), 15f + size.barHeight, backgroundPaint)
                }

                if (progress != null) {
                    canvas.drawRoundRect(rect, 2f, 2f, blurPaint)
                    canvas.drawRoundRect(rect, 2f, 2f, linePaint)

                    canvas.drawRoundRect(rect.right-4, rect.top, rect.right+4, rect.bottom, 2f, 2f, blurPaintHighlight)

                    if (showLabel){
                        val textBounds = textPaint.measureText(label)
                        val xOffset = (textBounds + 20).coerceAtLeast(10f) / 2f
                        val yOffset = when(size){
                            CustomProgressBarSize.SMALL -> (size.fontSize - size.barHeight) / 2 + 2f
                            CustomProgressBarSize.MEDIUM, CustomProgressBarSize.LARGE -> (size.fontSize - size.barHeight) / 2
                        }
                        val x = (rect.right - xOffset).coerceIn(0f..canvas.width-xOffset*2f)
                        val y = rect.top - yOffset
                        val r = x + xOffset * 2
                        val b = rect.bottom + yOffset

                        canvas.drawRoundRect(x, y, r, b, 2f, 2f, textBackgroundPaint)
                        canvas.drawRoundRect(x, y, r, b, 2f, 2f, blurPaint)
                        canvas.drawRoundRect(x, y, r, b, 2f, 2f, lineStrokePaint)

                        canvas.drawText(label, x + xOffset, rect.top + size.barHeight + 6, textPaint)
                    }
                }
            }
            PowerbarLocation.BOTTOM -> {
                val rect = RectF(
                    1f,
                    canvas.height.toFloat() - 1f - size.barHeight,
                    ((canvas.width.toDouble() - 1f) * (progress ?: 0.0).coerceIn(0.0, 1.0)).toFloat(),
                    canvas.height.toFloat()
                )

                if (barBackground){
                    canvas.drawRect(0f, canvas.height.toFloat() - size.barHeight, canvas.width.toFloat(), canvas.height.toFloat(), backgroundPaint)
                }

                if (progress != null) {
                    canvas.drawRoundRect(rect, 2f, 2f, blurPaint)
                    canvas.drawRoundRect(rect, 2f, 2f, linePaint)

                    canvas.drawRoundRect(rect.right-4, rect.top, rect.right+4, rect.bottom, 2f, 2f, blurPaintHighlight)

                    if (showLabel){
                        val textBounds = textPaint.measureText(label)
                        val xOffset = (textBounds + 20).coerceAtLeast(10f) / 2f
                        val yOffset = when(size){
                            CustomProgressBarSize.SMALL -> size.fontSize / 2 + 2f
                            CustomProgressBarSize.MEDIUM -> size.fontSize / 2
                            CustomProgressBarSize.LARGE -> size.fontSize / 2 - 5f
                        }
                        val x = (rect.right - xOffset).coerceIn(0f..canvas.width-xOffset*2f)
                        val y = (rect.top - yOffset)
                        val r = x + xOffset * 2
                        val b = rect.bottom + 5

                        canvas.drawRoundRect(x, y, r, b, 2f, 2f, textBackgroundPaint)
                        canvas.drawRoundRect(x, y, r, b, 2f, 2f, blurPaint)
                        canvas.drawRoundRect(x, y, r, b, 2f, 2f, lineStrokePaint)

                        canvas.drawText(label, x + xOffset, rect.top + size.barHeight - 1, textPaint)
                    }
                }
            }
        }
    }
}