package com.voidex.s2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.random.Random

/** Standalone target widget used only by VOIDEX's in-app training screen. */
class TargetTrainingView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var centerX = 0f
    private var centerY = 0f
    private val radius = 34f * resources.displayMetrics.density
    private var active = false
    private var shownAt = 0L
    var onTapResult: ((hit: Boolean, reactionMs: Long) -> Unit)? = null

    fun setActive(value: Boolean) {
        active = value
        if (value) shownAt = android.os.SystemClock.elapsedRealtime()
        invalidate()
    }

    fun resetTarget() {
        placeTarget()
        shownAt = android.os.SystemClock.elapsedRealtime()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        placeTarget()
    }

    private fun placeTarget() {
        val margin = radius + 14f * resources.displayMetrics.density
        val usableW = (width - margin * 2).coerceAtLeast(1f)
        val usableH = (height - margin * 2).coerceAtLeast(1f)
        centerX = margin + Random.nextFloat() * usableW
        centerY = margin + Random.nextFloat() * usableH
        shownAt = android.os.SystemClock.elapsedRealtime()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.rgb(31, 25, 44)
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 18f, 18f, paint)
        if (!active) {
            paint.color = android.graphics.Color.rgb(175, 160, 190)
            paint.textSize = 14f * resources.displayMetrics.scaledDensity
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("НАЖМИ СТАРТ", width / 2f, height / 2f, paint)
            return
        }
        paint.color = android.graphics.Color.rgb(255, 75, 135)
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(centerX, centerY, radius * 0.68f, paint)
        paint.color = android.graphics.Color.rgb(255, 75, 135)
        canvas.drawCircle(centerX, centerY, radius * 0.36f, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(centerX, centerY, radius * 0.11f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN || !active) return active
        val distance = hypot(event.x - centerX, event.y - centerY)
        val hit = distance <= radius
        val elapsed = (android.os.SystemClock.elapsedRealtime() - shownAt).coerceAtLeast(0L)
        onTapResult?.invoke(hit, elapsed)
        placeTarget()
        invalidate()
        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
