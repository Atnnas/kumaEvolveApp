package com.kuma.evolve

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.face.Face

class FaceGraphic(overlay: GraphicOverlay, private val face: Face) : GraphicOverlay.Graphic(overlay) {

    private val dotPaint = Paint().apply {
        color = Color.parseColor("#D4AF37") // Kuma Gold
        style = Paint.Style.FILL
        strokeWidth = 4f
    }

    private val linePaint = Paint().apply {
        color = Color.parseColor("#80D4AF37") // semi-transparent gold
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    override fun draw(canvas: Canvas) {
        // Draw the main points (matrix)
        for (landmark in face.allLandmarks) {
            val point = landmark.position
            val x = translateX(point.x)
            val y = translateY(point.y)
            canvas.drawCircle(x, y, 6f, dotPaint)
        }

        // Draw contours (this builds the "mesh" feeling)
        for (contour in face.allContours) {
            val points = contour.points
            for (i in 0 until points.size - 1) {
                canvas.drawLine(
                    translateX(points[i].x), translateY(points[i].y),
                    translateX(points[i + 1].x), translateY(points[i + 1].y),
                    linePaint
                )
            }
        }
    }
}
