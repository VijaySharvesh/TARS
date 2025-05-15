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
        isAntiAlias = true
    }

    init {
        // Create initial stars
        repeat(100) {
            stars.add(createStar())
        }
    }

    private fun createStar(): Star {
        return Star(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * height,
            radius = Random.nextFloat() * 2f + 0.5f,
            speed = Random.nextFloat() * 2f + 0.5f
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        stars.clear()
        repeat(100) {
            stars.add(createStar())
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw stars
        stars.forEach { star ->
            paint.alpha = (star.radius * 100).toInt()
            canvas.drawCircle(star.x, star.y, star.radius, paint)
            
            // Update star position
            star.y += star.speed
            if (star.y > height) {
                star.y = 0f
                star.x = Random.nextFloat() * width
            }
        }
        
        // Request next frame
        invalidate()
    }

    private data class Star(
        var x: Float,
        var y: Float,
        val radius: Float,
        val speed: Float
    )
} 