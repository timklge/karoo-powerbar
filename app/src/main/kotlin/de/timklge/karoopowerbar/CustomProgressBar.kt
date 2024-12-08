package de.timklge.karoopowerbar

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

class CustomProgressBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var progress: Double = 0.5
    var location: PowerbarLocation = PowerbarLocation.BOTTOM
    @ColorInt var progressColor: Int = 0xFF2b86e6.toInt()

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)

        val linePaint = Paint().apply {
            isAntiAlias = true
            strokeWidth = 1f
            style = Paint.Style.FILL_AND_STROKE
            color = progressColor
        }

        val blurPaint = Paint().apply {
            isAntiAlias = true
            strokeWidth = 2f
            style = Paint.Style.STROKE
            color = progressColor
            maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
        }

        val blurPaintHighlight = Paint().apply {
            isAntiAlias = true
            strokeWidth = 4f
            style = Paint.Style.FILL_AND_STROKE
            color = ColorUtils.blendARGB(progressColor, 0xFFFFFF, 0.5f)
            maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
        }

        val background = Paint().apply {
            style = Paint.Style.FILL_AND_STROKE
            color = Color.argb(1.0f, 0f, 0f, 0f)
            strokeWidth = 2f
        }

        when(location){
            PowerbarLocation.TOP -> {
                val rect = RectF(
                    1f,
                    1f,
                    ((canvas.width.toDouble() - 1f) * progress.coerceIn(0.0, 1.0)).toFloat(),
                    canvas.height.toFloat() - 1f - 4f
                )

                canvas.drawRoundRect(0f, 2f, canvas.width.toFloat(), canvas.height.toFloat() - 4f, 2f, 2f, background)

                if (progress > 0.0) {
                    canvas.drawRoundRect(rect, 2f, 2f, blurPaint)
                    canvas.drawRoundRect(rect, 2f, 2f, linePaint)
                    canvas.drawRoundRect(rect.right-4, rect.top, rect.right+4, rect.bottom, 2f, 2f, blurPaintHighlight)
                }
            }
            PowerbarLocation.BOTTOM -> {
                val rect = RectF(
                    1f,
                    1f + 4f,
                    ((canvas.width.toDouble() - 1f) * progress.coerceIn(0.0, 1.0)).toFloat(),
                    canvas.height.toFloat() - 1f
                )

                canvas.drawRoundRect(0f, 2f + 4f, canvas.width.toFloat(), canvas.height.toFloat(), 2f, 2f, background)

                if (progress > 0.0) {
                    canvas.drawRoundRect(rect, 2f, 2f, blurPaint)
                    canvas.drawRoundRect(rect, 2f, 2f, linePaint)
                    canvas.drawRoundRect(rect.right-4, rect.top, rect.right+4, rect.bottom, 2f, 2f, blurPaintHighlight)
                }
            }
        }
    }
}