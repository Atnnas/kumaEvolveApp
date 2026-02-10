package com.kuma.evolve

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.face.Face

class FaceGraphic(overlay: GraphicOverlay, private val face: Face) : GraphicOverlay.Graphic(overlay) {

    private val dotPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.FILL
        strokeWidth = 4f
    }

    private val linePaint = Paint().apply {
        color = Color.parseColor("#4000FFFF") // semi-transparent cyan
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    override fun draw(canvas: Canvas) {
        // Draw the main points (matrix)
        for (landmark in face.allLandmarks) {
            val point = landmark.position
            canvas.drawCircle(point.x, point.y, 6f, dotPaint)
        }

        // Draw contours (this builds the "mesh" feeling)
        for (contour in face.allContours) {
            val points = contour.points
            for (i in 0 until points.size - 1) {
                canvas.drawLine(
                    points[i].x, points[i].y,
                    points[i + 1].x, points[i + 1].y,
                    linePaint
                )
            }
        }
    }
}
