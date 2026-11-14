package com.voidex.s2

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class FloatingPanelService : Service() {
    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private var expanded = false
    private val handler = Handler(Looper.getMainLooper())
    private var secondsLeft = 30
    private var timerLabel: TextView? = null
    private var timerTask: Runnable? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 180
        }
        showBubble()
    }

    private fun showBubble() {
        removeRoot()
        expanded = false
        val bubble = TextView(this).apply {
            text = "V"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(235, 55, 125), 100)
            elevation = 12f
            setOnClickListener { showPanel() }
        }
        bubble.layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
        attachDrag(bubble)
        root = LinearLayout(this).apply { addView(bubble) }
        wm.addView(root, params)
    }

    private fun showPanel() {
        removeRoot()
        expanded = true
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(20, 15, 32), 18)
            elevation = 16f
        }
        val header = TextView(this).apply {
            text = "VOIDEX S2  •  QUICK PANEL"
            textSize = 13f
            setTextColor(Color.rgb(255, 105, 165))
            setPadding(0, 0, 0, dp(10))
        }
        attachDrag(header)
        panel.addView(header)
        panel.addView(TextView(this).apply {
            text = "Внешняя панель Android. Не читает данные матча и не отслеживает игроков."
            textSize = 11f
            setTextColor(Color.LTGRAY)
            maxWidth = dp(230)
            setPadding(0, 0, 0, dp(8))
        })
        timerLabel = TextView(this).apply {
            text = "Таймер: готов"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(8))
        }
        panel.addView(timerLabel)
        panel.addView(panelButton("ЗАПУСТИТЬ ТАЙМЕР 30 СЕК") { startTimer() })
        panel.addView(panelButton("СВЕРНУТЬ В V") { showBubble() })
        panel.addView(panelButton("ЗАКРЫТЬ ПАНЕЛЬ") { stopSelf() })
        root = panel
        wm.addView(root, params)
    }

    private fun startTimer() {
        timerTask?.let(handler::removeCallbacks)
        secondsLeft = 30
        val task = object : Runnable {
            override fun run() {
                timerLabel?.text = if (secondsLeft > 0) "Таймер: ${secondsLeft} сек" else "Таймер завершён"
                if (secondsLeft > 0) {
                    secondsLeft--
                    handler.postDelayed(this, 1000)
                }
            }
        }
        timerTask = task
        handler.post(task)
    }

    private fun panelButton(title: String, click: () -> Unit) = Button(this).apply {
        text = title
        textSize = 10f
        setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(105, 40, 90))
        setOnClickListener { click() }
    }

    private fun attachDrag(view: View) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    root?.let { try { wm.updateViewLayout(it, params) } catch (_: Exception) {} }
                    true
                }
                else -> false
            }
        }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun removeRoot() {
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        timerTask?.let(handler::removeCallbacks)
        removeRoot()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}