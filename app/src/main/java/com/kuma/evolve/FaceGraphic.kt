package com.kuma.evolve

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.face.Face

class FaceGraphic(overlay: GraphicOverlay, private val face: Face) : GraphicOverlay.Graphic(overlay) {

    private val dotPaint = Paint().apply {
        color = Color.parseColor("#FFD700") // Strong Gold
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.parseColor("#60FFD700") // Brighter semi-transparent gold
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    override fun draw(canvas: Canvas) {
        // Draw the main landmarks (high density)
        for (landmark in face.allLandmarks) {
            val point = landmark.position
            val x = translateX(point.x)
            val y = translateY(point.y)
            canvas.drawCircle(x, y, 7f, dotPaint)
        }

        // Draw ALL contour points for a "High-Density Scanning Matrix" effect
        for (contour in face.allContours) {
            val points = contour.points
            for (i in 0 until points.size) {
                val x = translateX(points[i].x)
                val y = translateY(points[i].y)
                
                // Draw small dots at every point of the contour
                canvas.drawCircle(x, y, 4f, dotPaint)

                // Connect with lines for a "strong" mesh
                if (i < points.size - 1) {
                    canvas.drawLine(
                        x, y,
                        translateX(points[i + 1].x), translateY(points[i + 1].y),
                        linePaint
                    )
                }
            }
        }
    }
}
