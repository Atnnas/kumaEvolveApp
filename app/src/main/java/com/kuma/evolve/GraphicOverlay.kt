package com.kuma.evolve

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

class GraphicOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val lock = Any()
    private val graphics = mutableListOf<Graphic>()

    private var imageWidth = 0
    private var imageHeight = 0
    private var scaleX = 1f
    private var scaleY = 1f
    private var isFrontCamera = true

    abstract class Graphic(protected val overlay: GraphicOverlay) {
        abstract fun draw(canvas: Canvas)

        fun translateX(x: Float): Float = if (overlay.isFrontCamera) {
            overlay.width - (x * overlay.scaleX)
        } else {
            x * overlay.scaleX
        }

        fun translateY(y: Float): Float = y * overlay.scaleY
    }

    fun setCameraInfo(width: Int, height: Int, isFront: Boolean) {
        synchronized(lock) {
            imageWidth = width
            imageHeight = height
            isFrontCamera = isFront
            calculateScale()
        }
    }

    private fun calculateScale() {
        if (imageWidth == 0 || imageHeight == 0) return
        scaleX = width.toFloat() / imageWidth.toFloat()
        scaleY = height.toFloat() / imageHeight.toFloat()
    }

    fun clear() {
        synchronized(lock) {
            graphics.clear()
        }
        postInvalidate()
    }

    fun add(graphic: Graphic) {
        synchronized(lock) {
            graphics.add(graphic)
        }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(lock) {
            if (imageWidth != 0 && imageHeight != 0) {
                calculateScale()
            }
            for (graphic in graphics) {
                graphic.draw(canvas)
            }
        }
    }
}
