package com.kuma.evolve

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.sin

class EnrollmentOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progressPercent = 0f
    private var currentStage = -1 

    private val paintProgressBg = Paint().apply {
        color = ContextCompat.getColor(context, R.color.kuma_white)
        alpha = 60
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val paintProgress = Paint().apply {
        color = ContextCompat.getColor(context, R.color.kuma_gold)
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val paintGuide = Paint().apply {
        color = ContextCompat.getColor(context, R.color.kuma_gold)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun setProgressPercentage(percent: Float) {
        progressPercent = percent.coerceIn(0f, 100f)
        invalidate()
    }

    fun setCurrentStage(stage: Int) {
        currentStage = stage
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height * 0.45f
        val radius = width * 0.38f
        val rect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        // 1. Draw Simple Background Ring
        canvas.drawCircle(centerX, centerY, radius, paintProgressBg)

        // 2. Draw Progress (Single line)
        if (progressPercent > 0) {
            canvas.drawArc(rect, -90f, (progressPercent / 100f) * 360f, false, paintProgress)
        }

        // 3. Draw Directional Guides (Arrows only)
        drawDirectionalGuide(canvas, centerX, centerY, radius)
    }

    private fun drawDirectionalGuide(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val arrowSize = 35f
        val distance = radius + 60f
        
        // Stages: 0:Center, 1:Left, 2:Right, 3:Up, 4:Down
        when (currentStage) {
            1 -> drawArrow(canvas, cx - distance, cy, 180f, arrowSize) // Left
            2 -> drawArrow(canvas, cx + distance, cy, 0f, arrowSize)   // Right
            3 -> drawArrow(canvas, cx, cy - distance, 270f, arrowSize) // Up
            4 -> drawArrow(canvas, cx, cy + distance, 90f, arrowSize)  // Down
        }
    }

    private fun drawArrow(canvas: Canvas, x: Float, y: Float, angle: Float, size: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(angle)
        
        val path = Path().apply {
            moveTo(size, 0f)
            lineTo(-size / 2, -size)
            lineTo(-size / 2, size)
            close()
        }
        
        canvas.drawPath(path, paintGuide)
        canvas.restore()
    }
}
