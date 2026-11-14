package com.voidex.s2

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.content.pm.ServiceInfo
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Independent Android overlay renderer.
 *
 * Marker data is intentionally empty until an authorized provider is connected.
 * This service does not inspect the game surface, process memory, files or network traffic.
 */
class FloatingPanelService : Service() {
    companion object {
        private const val CHANNEL_ID = "voidex_overlay"
        private const val NOTIFICATION_ID = 2101
        const val ACTION_STOP = "com.voidex.s2.action.STOP_OVERLAY"
        const val ACTION_UPDATE_CANDIDATE_BOXES = "com.voidex.s2.action.UPDATE_CANDIDATE_BOXES"
        const val EXTRA_BOXES = "candidate_boxes"
    }
    private lateinit var wm: WindowManager
    private var root: View? = null
    private var expanded = false
    private val handler = Handler(Looper.getMainLooper())
    private var secondsLeft = 30
    private var timerLabel: TextView? = null
    private var timerTask: Runnable? = null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var renderParams: WindowManager.LayoutParams
    private var renderer: OverlayRendererView? = null
    private var showBoxes = true
    private var showNames = true
    private var showDistance = true
    private var showHealth = false

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
        renderParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        val settings = getSharedPreferences("voidex_overlay", MODE_PRIVATE)
        showBoxes = settings.getBoolean("boxes", true)
        showNames = settings.getBoolean("names", true)
        showDistance = settings.getBoolean("distance", true)
        showHealth = settings.getBoolean("health", false)
        renderer = OverlayRendererView(this)
        renderer?.configure(showBoxes, showNames, showDistance, showHealth)
        wm.addView(renderer, renderParams)
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE_CANDIDATE_BOXES) {
            val values = intent.getFloatArrayExtra(EXTRA_BOXES) ?: FloatArray(0)
            val items = values.asList().chunked(4).filter { it.size == 4 }.map { v ->
                OverlayMarker(RectF(v[0], v[1], v[2], v[3]), "КАНДИДАТ")
            }
            renderer?.submitMarkers(items)
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureNotificationChannel()
        val stopIntent = Intent(this, FloatingPanelService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = builder.setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("VOIDEX S2 Overlay активен")
            .setContentText("Нажми, чтобы открыть VOIDEX S2")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stopPending)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "VOIDEX Overlay", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Постоянное уведомление работающего оверлея"
                    }
                )
            }
        }
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
            text = "VOIDEX S2  •  OVERLAY"
            textSize = 13f
            setTextColor(Color.rgb(255, 105, 165))
            setPadding(0, 0, 0, dp(10))
        }
        attachDrag(header)
        panel.addView(header)
        panel.addView(TextView(this).apply {
            text = "Рендерер готов. Источник разрешённых маркеров не подключён."
            textSize = 11f
            setTextColor(Color.LTGRAY)
            maxWidth = dp(240)
            setPadding(0, 0, 0, dp(8))
        })
        panel.addView(toggleRow("Рамки (Box)", showBoxes) { showBoxes = it; refreshRenderer() })
        panel.addView(toggleRow("Имена (Name)", showNames) { showNames = it; refreshRenderer() })
        panel.addView(toggleRow("Дистанция", showDistance) { showDistance = it; refreshRenderer() })
        panel.addView(toggleRow("Индикатор здоровья", showHealth) { showHealth = it; refreshRenderer() })
        timerLabel = TextView(this).apply {
            text = "Таймер: готов"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, dp(7), 0, dp(7))
        }
        panel.addView(timerLabel)
        panel.addView(panelButton("ЗАПУСТИТЬ ТАЙМЕР 30 СЕК") { startTimer() })
        panel.addView(panelButton("СВЕРНУТЬ В V") { showBubble() })
        panel.addView(panelButton("ЗАКРЫТЬ ОВЕРЛЕЙ") { stopSelf() })
        root = panel
        wm.addView(root, params)
    }

    private fun toggleRow(title: String, initial: Boolean, changed: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val label = TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(Color.WHITE)
        }
        row.addView(label, LinearLayout.LayoutParams(0, dp(38), 1f))
        val toggle = android.widget.Switch(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> changed(checked) }
        }
        row.addView(toggle)
        return row
    }

    /**
     * Publishes screen-space markers from an explicitly authorized provider.
     * This service itself never discovers or calculates player positions.
     */
    fun submitMarkers(items: List<OverlayMarker>) {
        renderer?.submitMarkers(items)
    }

    private fun refreshRenderer() {
        getSharedPreferences("voidex_overlay", MODE_PRIVATE).edit()
            .putBoolean("boxes", showBoxes)
            .putBoolean("names", showNames)
            .putBoolean("distance", showDistance)
            .putBoolean("health", showHealth)
            .apply()
        renderer?.configure(showBoxes, showNames, showDistance, showHealth)
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
        var dragged = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (kotlin.math.abs(dx) > dp(5) || kotlin.math.abs(dy) > dp(5)) dragged = true
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    root?.let { try { wm.updateViewLayout(it, params) } catch (_: Exception) {} }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) view.performClick()
                    true
                }
                else -> true
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
        timerTask = null
        removeRoot()
        renderer?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        renderer = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/** Draws only marker records explicitly supplied by a future permitted data provider. */
data class OverlayMarker(
    val bounds: RectF,
    val label: String,
    val distanceMeters: Float? = null,
    val healthFraction: Float? = null
)

class OverlayRendererView(
    private val service: FloatingPanelService
) : View(service) {
    private val markers = mutableListOf<OverlayMarker>()
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 70, 145)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        typeface = Typeface.DEFAULT_BOLD
    }
    private var boxes = true
    private var names = true
    private var distances = true
    private var health = false

    fun configure(boxes: Boolean, names: Boolean, distances: Boolean, health: Boolean) {
        this.boxes = boxes
        this.names = names
        this.distances = distances
        this.health = health
        invalidate()
    }

    /** A permitted provider can publish already-authorized, screen-space marker data here. */
    fun submitMarkers(items: List<OverlayMarker>) {
        markers.clear()
        markers.addAll(items)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        markers.forEach { marker ->
            if (boxes) canvas.drawRoundRect(marker.bounds, 8f, 8f, boxPaint)
            var y = marker.bounds.top - 10f
            if (names) {
                canvas.drawText(marker.label, marker.bounds.left, y, textPaint)
                y -= textPaint.textSize + 5f
            }
            if (distances && marker.distanceMeters != null) {
                canvas.drawText("${marker.distanceMeters.toInt()} m", marker.bounds.left, y, textPaint)
            }
            if (health && marker.healthFraction != null) {
                val fraction = marker.healthFraction.coerceIn(0f, 1f)
                val bar = RectF(marker.bounds.left, marker.bounds.bottom + 8f,
                    marker.bounds.left + marker.bounds.width() * fraction,
                    marker.bounds.bottom + 14f)
                canvas.drawRect(bar, boxPaint)
            }
        }
    }
}
