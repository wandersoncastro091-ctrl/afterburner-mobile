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

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var panel: View? = null
    private lateinit var cpu: TextView
    private lateinit var ram: TextView
    private lateinit var batteryTemp: TextView
    private lateinit var batteryLevel: TextView
    private var previous: Pair<Long, Long>? = null
    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            val now = readCpu()
            val old = previous
            if (now != null && old != null) {
                val total = now.first - old.first
                val idle = now.second - old.second
                if (total > 0) cpu.text = ((total - idle) * 100 / total).coerceIn(0, 100).toString() + "%"
            }
            if (now != null) previous = now
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
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotification()
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
        val yellow = Color.rgb(255, 218, 70)
        val muted = Color.rgb(205, 205, 205)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 9, 12, 8)
            background = GradientDrawable().apply {
                setColor(Color.rgb(5, 7, 8))
                setStroke(2, Color.rgb(75, 85, 82))
                cornerRadius = 5f
            }
        }
        val title = TextView(this).apply {
            text = "MSI AFTERBURNER  •  HARDWARE MONITOR   ⋮ HOLD TO MOVE"
            setTextColor(Color.rgb(170, 245, 190))
            textSize = 13f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setPadding(0, 0, 0, 5)
        }
        root.addView(title)

        addMetricRow(root, "GPU", "N/A °C", green, yellow, "MEM", "N/A MB", cyan, yellow)
        val cpuValues = addMetricRow(root, "CPU", "N/A °C", cyan, yellow, "USAGE", "N/A%", cyan, yellow)
        cpu = cpuValues.second!!
        ram = addMetricRow(root, "RAM", "N/A", green, yellow).first
        addMetricRow(root, "D3D11", "N/A FPS", Color.WHITE, yellow)
        addMetricRow(root, "Frametime", "N/A ms", Color.WHITE, yellow)
        val batteryValues = addMetricRow(root, "BATTERY", "N/A °C", cyan, yellow, "LEVEL", "N/A%", green, yellow)
        batteryTemp = batteryValues.first
        batteryLevel = batteryValues.second!!

        val close = TextView(this).apply {
            text = "× CLOSE OVERLAY"
            textSize = 10f
            gravity = Gravity.END
            setTextColor(muted)
            setPadding(0, 5, 0, 0)
            setOnClickListener { stopSelf() }
        }
        root.addView(close)

        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 90
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        title.setOnTouchListener { _, event ->
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
            setPadding(0, 3, 9, 3)
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