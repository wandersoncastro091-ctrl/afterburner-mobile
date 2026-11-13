package com.example.afterburnermobile

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.provider.Settings
import android.widget.*
import java.io.File
import java.util.Locale

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var panel: View? = null
    private lateinit var cpu: TextView
    private lateinit var ram: TextView
    private lateinit var battery: TextView
    private var previous: Pair<Long, Long>? = null
    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            val now = readCpu()
            val old = previous
            if (now != null && old != null) {
                val total = now.first - old.first
                val idle = now.second - old.second
                if (total > 0) cpu.text = "CPU USAGE   " + ((total - idle) * 100 / total).coerceIn(0, 100) + "%"
            }
            if (now != null) previous = now
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mem)
            val used = if (mem.totalMem > 0) ((mem.totalMem - mem.availMem) * 100 / mem.totalMem).toInt() else 0
            ram.text = "RAM USAGE   " + used + "%"
            val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val t = b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val l = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val temp = if (t >= 0) String.format(Locale.US, "%.1f", t / 10f) else "--.-"
            val charge = if (l >= 0 && scale > 0) (l * 100 / scale).toString() else "--"
            battery.text = "BATTERY TEMP   " + temp + " °C     BATTERY   " + charge + "%"
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
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channel)
        } else {
            Notification.Builder(this)
        }.setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Afterburner Mobile")
            .setContentText("Hardware monitor overlay is active")
            .setOngoing(true).build()
        startForeground(77, notification)
    }

    private fun showPanel() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permissão de sobreposição não está ativada.", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 10, 14, 10)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.rgb(25, 31, 27))
                setStroke(3, android.graphics.Color.rgb(105, 120, 110))
                cornerRadius = 12f
            }
        }
        val title = TextView(this).apply {
            text = "MSI  AFTERBURNER  •  HARDWARE MONITOR"
            setTextColor(android.graphics.Color.rgb(190, 220, 170))
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(title)
        addLine(root, "GPU TEMP   N/A °C       GPU USAGE   N/A%")
        cpu = addLine(root, "CPU USAGE   N/A%")
        ram = addLine(root, "RAM USAGE   N/A%")
        addLine(root, "FRAMERATE   N/A FPS       FRAMETIME   N/A ms")
        battery = addLine(root, "BATTERY TEMP   --.- °C")
        val close = TextView(this).apply {
            text = "× CLOSE OVERLAY"
            textSize = 11f
            gravity = Gravity.END
            setTextColor(android.graphics.Color.LTGRAY)
            setPadding(0, 8, 0, 0)
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

    private fun addLine(parent: LinearLayout, value: String): TextView {
        val line = TextView(this).apply {
            text = value
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 7, 0, 7)
        }
        parent.addView(line)
        return line
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