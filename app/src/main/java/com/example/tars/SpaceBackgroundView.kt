package com.example.tars

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class SpaceBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val stars = mutableListOf<Star>()
    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    init {
        // Create initial stars
        for (i in 0 until 100) {
            stars.add(createStar())
        }
    }

    private fun createStar(): Star {
        return Star(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * height,
            size = Random.nextFloat() * 3f,
            speed = Random.nextFloat() * 2f + 1f
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Recreate stars when view size changes
        stars.clear()
        for (i in 0 until 100) {
            stars.add(createStar())
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw stars
        stars.forEach { star ->
            paint.alpha = (star.size * 85).toInt()
            canvas.drawCircle(star.x, star.y, star.size, paint)
        }

        // Update star positions
        stars.forEach { star ->
            star.y += star.speed
            if (star.y > height) {
                star.y = 0f
                star.x = Random.nextFloat() * width
                star.size = Random.nextFloat() * 3f
                star.speed = Random.nextFloat() * 2f + 1f
            }
        }

        // Request next frame
        invalidate()
    }

    private data class Star(
        var x: Float,
        var y: Float,
        var size: Float,
        var speed: Float
    )
} 