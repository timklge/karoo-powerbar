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
    var progress: Double = 0.5
    var location: PowerbarLocation = PowerbarLocation.BOTTOM
    var label: String = ""
    var showLabel: Boolean = true
    @ColorInt var progressColor: Int = 0xFF2b86e6.toInt()

    val fontSize = 40f

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
        textSize = fontSize
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
                    ((canvas.width.toDouble() - 1f) * progress.coerceIn(0.0, 1.0)).toFloat(),
                    15f + 20f
                )

                canvas.drawRoundRect(0f, 15f, canvas.width.toFloat(), 15f + 20f, 2f, 2f, backgroundPaint)

                if (progress > 0.0) {
                    canvas.drawRoundRect(rect, 2f, 2f, blurPaint)
                    canvas.drawRoundRect(rect, 2f, 2f, linePaint)

                    canvas.drawRoundRect(rect.right-4, rect.top, rect.right+4, rect.bottom, 2f, 2f, blurPaintHighlight)

                    if (showLabel){
                        val textBounds = textPaint.measureText(label)
                        val xOffset = (textBounds + 20).coerceAtLeast(10f) / 2f
                        val yOffset = (fontSize - 15f) / 2
                        val x = (rect.right - xOffset).coerceIn(0f..canvas.width-xOffset*2f)
                        val y = rect.top - yOffset
                        val r = x + xOffset * 2
                        val b = rect.bottom + yOffset

                        canvas.drawRoundRect(x, y, r, b, 2f, 2f, textBackgroundPaint)
                        canvas.drawRoundRect(x, y, r, b, 2f, 2f, blurPaint)
                        canvas.drawRoundRect(x, y, r, b, 2f, 2f, lineStrokePaint)

                        canvas.drawText(label, x + xOffset, rect.top + 23, textPaint)
                    }
                }
            }
            PowerbarLocation.BOTTOM -> {
                val rect = RectF(
                    1f,
                    canvas.height.toFloat() - 1f - 20f,
                    ((canvas.width.toDouble() - 1f) * progress.coerceIn(0.0, 1.0)).toFloat(),
                    canvas.height.toFloat() - 1f
                )

                canvas.drawRoundRect(0f, canvas.height.toFloat() - 20f, canvas.width.toFloat(), canvas.height.toFloat(), 2f, 2f, backgroundPaint)

                if (progress > 0.0) {
                    canvas.drawRoundRect(rect, 2f, 2f, blurPaint)
                    canvas.drawRoundRect(rect, 2f, 2f, linePaint)

                    canvas.drawRoundRect(rect.right-4, rect.top, rect.right+4, rect.bottom, 2f, 2f, blurPaintHighlight)

                    if (showLabel){
                        val textBounds = textPaint.measureText(label)
                        val xOffset = (textBounds + 20).coerceAtLeast(10f) / 2f
                        val yOffset = (fontSize + 0f) / 2
                        val x = (rect.right - xOffset).coerceIn(0f..canvas.width-xOffset*2f)
                        val y = (rect.top - yOffset)
                        val r = x + xOffset * 2
                        val b = rect.bottom + 5

                        canvas.drawRoundRect(x, y, r, b, 2f, 2f, textBackgroundPaint)
                        canvas.drawRoundRect(x, y, r, b, 2f, 2f, blurPaint)
                        canvas.drawRoundRect(x, y, r, b, 2f, 2f, lineStrokePaint)

                        canvas.drawText(label, x + xOffset, rect.top + 16, textPaint)
                    }
                }
            }
        }
    }
}