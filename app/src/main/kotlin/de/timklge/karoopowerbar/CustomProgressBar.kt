package de.timklge.karoopowerbar

import android.content.Context
import android.content.res.Configuration
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

class CustomProgressBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val mPaddedRect = RectF()
    private val mBlurRadius = 4

    var progress: Double = 0.5
    @ColorInt var progressColor: Int = 0xFF2b86e6.toInt()

    fun isDarkMode(context: Context): Boolean {
        val flags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return flags == Configuration.UI_MODE_NIGHT_YES
    }

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
            color = 0x70000000
            strokeWidth = 2f
        }

        val halfStrokeWidth = 1f
        mPaddedRect.left = halfStrokeWidth
        mPaddedRect.top = halfStrokeWidth + mBlurRadius
        mPaddedRect.right =
            ((canvas.width.toDouble() - halfStrokeWidth) * progress.coerceIn(0.0, 1.0)).toFloat()
        mPaddedRect.bottom = canvas.height.toFloat() - halfStrokeWidth

        val corners = 2f
        canvas.drawRoundRect(0f, 2f + mBlurRadius, canvas.width.toFloat(), canvas.height.toFloat(), 2f, 2f, background)
        canvas.drawRoundRect(mPaddedRect, corners, corners, blurPaint)
        canvas.drawRoundRect(mPaddedRect, corners, corners, linePaint)

        canvas.drawRoundRect(mPaddedRect.right-4, mPaddedRect.top, mPaddedRect.right+4, mPaddedRect.bottom, 2f, 2f, blurPaintHighlight)
    }
}