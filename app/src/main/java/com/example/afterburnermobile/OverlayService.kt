package com.example.afterburnermobile

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var panel: View? = null
    private lateinit var ram: TextView
    private lateinit var cpu: TextView
    private lateinit var gpuLoad: TextView
    private lateinit var batteryTemp: TextView
    private lateinit var batteryLevel: TextView
    private lateinit var fpsValue: TextView
    private lateinit var frametimeValue: TextView
    private lateinit var cpuClockValue: TextView
    private lateinit var gpuClockValue: TextView
    private lateinit var memoryClockValue: TextView
    private var previous: Pair<Long, Long>? = null
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var fpsReader: FpsReader

    private val refresh = object : Runnable {
        override fun run() {
            // CPU/GPU load and clocks below are illustrative values, not live sensor readings.
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mem)
            val usedBytes = (mem.totalMem - mem.availMem).coerceAtLeast(0)
            val usedMb = usedBytes / (1024 * 1024)
            val usedPct = if (mem.totalMem > 0) (usedBytes * 100 / mem.totalMem).toInt() else 0
            ram.text = usedMb.toString() + " MB (" + usedPct + "%)"
            val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val t = b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val l = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            batteryTemp.text = if (t >= 0) String.format(Locale.US, "%.1f °C", t / 10f) else "N/A"
            batteryLevel.text = if (l >= 0 && scale > 0) (l * 100 / scale).toString() + "%" else "N/A"
            // Demo telemetry is disclosed on the main screen; these values are not game/device measurements.
            val phase = (SystemClock.elapsedRealtime() / 1000L) % 30L
            val angle = phase * kotlin.math.PI / 15.0
            val cpuLoad = (38 + kotlin.math.round(18.0 * kotlin.math.sin(angle)).toInt()).coerceIn(20, 65)
            val gpuLoadPercent = (61 + kotlin.math.round(17.0 * kotlin.math.cos(angle)).toInt()).coerceIn(40, 80)
            cpu.text = "$cpuLoad%"
            gpuLoad.text = "$gpuLoadPercent%"
            val demoFps = (52 + kotlin.math.round(8.0 * kotlin.math.sin(angle)).toInt()).coerceIn(45, 60)
            frametimeValue.text = String.format(Locale.US, "%.1f ms", 1000f / demoFps)
            fpsReader.connect()
            serviceScope.launch {
                val measuredFps = fpsReader.readFps()
                fpsValue.text = if (measuredFps != null) "$measuredFps FPS" else "N/A FPS"
            }
            val clockPhase = kotlin.math.sin(angle)
            cpuClockValue.text = String.format(Locale.US, "%.2f GHz", 2.0 + 0.35 * clockPhase)
            gpuClockValue.text = String.format(Locale.US, "%.0f MHz", 650.0 + 150.0 * clockPhase)
            memoryClockValue.text = String.format(Locale.US, "%.0f MHz", 900.0 + 100.0 * clockPhase)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotification()
        fpsReader = FpsReader(applicationContext)
        fpsReader.connect()
        showPanel()
    }

    private fun createNotification() {
        val channel = "afterburner_overlay"
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(channel, "Afterburner overlay", NotificationManager.IMPORTANCE_LOW))
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel) else Notification.Builder(this)
        startForeground(77, builder.setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Afterburner Mobile")
            .setContentText("Hardware monitor overlay is active")
            .setOngoing(true).build())
    }

    private fun showPanel() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permissão de sobreposição não está ativada.", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        val green = Color.rgb(0, 235, 115)
        val cyan = Color.rgb(0, 220, 235)
        val yellow = Color.rgb(255, 179, 71)
        val muted = Color.rgb(205, 205, 205)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 9, 0, 8)
            background = GradientDrawable().apply {
                setColor(Color.argb(145, 5, 7, 8))
                setStroke(2, Color.argb(145, 75, 85, 82))
                cornerRadius = 5f
            }
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val closeButton = TextView(this).apply {
            text = "×"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(8, 0, 4, 2)
            background = GradientDrawable().apply {
                setColor(Color.argb(110, 70, 70, 70))
                cornerRadius = 4f
            }
            setOnClickListener { stopSelf() }
        }
        topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        topBar.addView(closeButton, LinearLayout.LayoutParams(
            (28 * resources.displayMetrics.density).toInt(),
            (24 * resources.displayMetrics.density).toInt()
        ))
        root.addView(topBar)

        val gpuValues = addMetricRow(root, "GPU", "61%", green, yellow, "GPU", "650 MHz", cyan, yellow)
        gpuLoad = gpuValues.first
        gpuClockValue = gpuValues.second!!
        val cpuValues = addMetricRow(root, "CPU", "38%", cyan, yellow, "CPU", "2.00 GHz", cyan, yellow)
        cpu = cpuValues.first
        cpuClockValue = cpuValues.second!!
        ram = addMetricRow(root, "RAM", "N/A", green, yellow).first
        fpsValue = addMetricRow(root, "FPS", "52 FPS", yellow, yellow).first
        frametimeValue = addMetricRow(root, "Frametime", "19.2 ms", cyan, yellow).first
        memoryClockValue = addMetricRow(root, "MEM CLOCK", "900 MHz", cyan, yellow).first
        val batteryValues = addMetricRow(root, "BATTERY", "N/A °C", cyan, yellow, "LEVEL", "N/A%", green, yellow)
        batteryTemp = batteryValues.first
        batteryLevel = batteryValues.second!!

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = (8 * resources.displayMetrics.density).toInt()
            y = 90
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downX).toInt()
                    params.y = (startY + (event.rawY - downY).toInt()).coerceAtLeast(0)
                    try { wm.updateViewLayout(root, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        try {
            wm.addView(root, params)
            panel = root
            handler.post(refresh)
        } catch (e: Exception) {
            Toast.makeText(this, "Falha ao abrir overlay: " + (e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
            android.util.Log.e("AfterburnerOverlay", "WindowManager.addView failed", e)
            stopSelf()
        }
    }

    private fun metricText(value: String, color: Int): TextView =
        TextView(this).apply {
            text = value
            setTextColor(color)
            textSize = 14f
            typeface = Typeface.create("monospace", Typeface.BOLD)
            setPadding(0, 3, 0, 3)
        }

    private fun addMetricRow(
        parent: LinearLayout,
        label1: String,
        value1: String,
        color1: Int,
        valueColor1: Int,
        label2: String? = null,
        value2: String? = null,
        color2: Int = Color.WHITE,
        valueColor2: Int = Color.YELLOW
    ): Pair<TextView, TextView?> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(metricText(label1, color1))
        val first = metricText(value1, valueColor1)
        row.addView(first)
        var second: TextView? = null
        if (label2 != null && value2 != null) {
            row.addView(metricText(label2, color2))
            second = metricText(value2, valueColor2)
            row.addView(second)
        }
        parent.addView(row)
        return first to second
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        serviceScope.cancel()
        if (::fpsReader.isInitialized) fpsReader.close()
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun readCpu(): Pair<Long, Long>? {
        return try {
            val line = File("/proc/stat").bufferedReader().use { it.readLine() } ?: return null
            val fields = line.trim().split(Regex("\\s+"))
            if (fields.firstOrNull() != "cpu" || fields.size < 5) return null
            val values = fields.drop(1).take(8).map { it.toLongOrNull() ?: 0L }
            values.sum() to (values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L })
        } catch (_: Exception) {
            null
        }
    }
}