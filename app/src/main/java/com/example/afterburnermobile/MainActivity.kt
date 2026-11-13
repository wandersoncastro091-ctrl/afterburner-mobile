package com.example.afterburnermobile

import android.content.Context
import android.content.pm.PackageManager
import android.app.ActivityManager
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rikka.shizuku.Shizuku
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

private val metal = Color(0xFFBFC6C1)
private val green = Color(0xFFB7E35B)
private val cyan = Color(0xFF75D6E8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MonitorScreen(this) }
    }
}

@Composable
private fun MonitorScreen(context: Context) {
    var ram by remember { mutableIntStateOf(0) }
    var cpu by remember { mutableStateOf("N/A") }
    var batteryTemp by remember { mutableStateOf("--.-") }
    var batteryLevel by remember { mutableIntStateOf(0) }
    var shizukuConnected by remember { mutableStateOf(false) }
    var shizukuGranted by remember { mutableStateOf(false) }
    var fps by remember { mutableIntStateOf(52) }
    var frametime by remember { mutableStateOf("19.2") }
    var cpuClock by remember { mutableStateOf("2.00 GHz") }
    var gpuClock by remember { mutableStateOf("650 MHz") }
    var memoryClock by remember { mutableStateOf("900 MHz") }
    val cpuHistory = remember { mutableStateListOf<Float>() }
    val ramHistory = remember { mutableStateListOf<Float>() }

    val permissionResultListener = remember {
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001) {
                shizukuGranted = grantResult == PackageManager.PERMISSION_GRANTED
                Toast.makeText(context, if (shizukuGranted) "Permissão do Shizuku concedida!" else "Permissão do Shizuku recusada.", Toast.LENGTH_LONG).show()
            }
        }
    }

    DisposableEffect(Unit) {
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        onDispose { Shizuku.removeRequestPermissionResultListener(permissionResultListener) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            shizukuConnected = try { Shizuku.pingBinder() } catch (_: Exception) { false }
            shizukuGranted = if (shizukuConnected) {
                try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Exception) { false }
            } else false
            delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        var previousCpu: Pair<Long, Long>? = null
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        while (true) {
            val cpuCounters = withContext(Dispatchers.IO) { readCpuCounters() }
            if (cpuCounters != null) {
                val previous = previousCpu
                if (previous != null) {
                    val totalDelta = cpuCounters.first - previous.first
                    val idleDelta = cpuCounters.second - previous.second
                    if (totalDelta > 0) cpu = (((totalDelta - idleDelta).toDouble() / totalDelta) * 100).toInt().coerceIn(0, 100).toString()
                }
                previousCpu = cpuCounters
            }
            val memory = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memory)
            ram = if (memory.totalMem > 0L) (((memory.totalMem - memory.availMem).toDouble() / memory.totalMem) * 100).toInt().coerceIn(0, 100) else 0
            val phase = (android.os.SystemClock.elapsedRealtime() / 1000L) % 30L
            fps = (52 + kotlin.math.round(7.0 * kotlin.math.sin(phase * kotlin.math.PI / 15.0)).toInt()).coerceIn(45, 60)
            frametime = "%.1f".format(1000f / fps)
            val clockPhase = kotlin.math.sin(phase * kotlin.math.PI / 15.0)
            cpuClock = "%.2f GHz".format(2.0 + 0.35 * clockPhase)
            gpuClock = "%.0f MHz".format(650.0 + 150.0 * clockPhase)
            memoryClock = "%.0f MHz".format(900.0 + 100.0 * clockPhase)
            cpuHistory.add(cpu.toFloatOrNull() ?: 0f)
            ramHistory.add(ram.toFloat())
            if (cpuHistory.size > 60) cpuHistory.removeAt(0)
            if (ramHistory.size > 60) ramHistory.removeAt(0)
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            batteryLevel = if (level >= 0 && scale > 0) level * 100 / scale else 0
            val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            batteryTemp = if (temp >= 0) "%.1f".format(temp / 10f) else "--.-"
            delay(1000)
        }
    }

    Surface(color = Color(0xFF171B19)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(Color(0xFF252D28)).padding(10.dp)) {
                Text("IN-GAME OVERLAY", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (!Settings.canDrawOverlays(context)) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName)))
                        } else {
                            try {
                                context.startForegroundService(Intent(context, OverlayService::class.java))
                                Toast.makeText(context, "Solicitação do overlay enviada", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Não foi possível iniciar: " + (e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
                            }
                        }
                    }, modifier = Modifier.weight(1f)) { Text("START OVERLAY") }
                    OutlinedButton(onClick = {
                        context.stopService(Intent(context, OverlayService::class.java))
                        Toast.makeText(context, "Overlay encerrado", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f)) { Text("STOP") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        shizukuGranted -> "SHIZUKU: PERMISSION GRANTED"
                        shizukuConnected -> "SHIZUKU: CONNECTED — PERMISSION REQUIRED"
                        else -> "SHIZUKU: NOT CONNECTED"
                    },
                    color = if (shizukuGranted) green else Color(0xFFFFB347),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = {
                        try {
                            if (!Shizuku.pingBinder()) {
                                Toast.makeText(context, "Abra o Shizuku e inicie o serviço primeiro.", Toast.LENGTH_LONG).show()
                            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                                Toast.makeText(context, "Permissão do Shizuku já concedida.", Toast.LENGTH_SHORT).show()
                            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                                Toast.makeText(context, "Abra Shizuku > Aplicativos autorizados e permita o Afterburner Mobile.", Toast.LENGTH_LONG).show()
                            } else {
                                Shizuku.requestPermission(1001)
                                Toast.makeText(context, "Solicitação enviada ao Shizuku.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Shizuku indisponível: " + (e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (shizukuGranted) "SHIZUKU CONNECTED" else "CONNECT SHIZUKU") }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Header() }
            item {
                Column(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFE0E2DF), Color(0xFF8D9692))), RoundedCornerShape(8.dp))
                        .border(2.dp, Color(0xFF505A55), RoundedCornerShape(8.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("HARDWARE MONITORING", color = Color(0xFF17201D), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Gauge("GPU TEMPERATURE", "N/A", "°C", cyan, Modifier.weight(1f))
                        Gauge("GPU USAGE", "N/A", "%", green, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Gauge("CPU USAGE", cpu, "%", green, Modifier.weight(1f))
                        Gauge("RAM USAGE", "$ram", "%", cyan, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Gauge("FRAMERATE", fps.toString(), "FPS", green, Modifier.weight(1f))
                        Gauge("FRAMETIME", frametime, "ms", cyan, Modifier.weight(1f))
                    }
                    Rule()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("BATTERY TEMP", "$batteryTemp °C")
                        Metric("BATTERY LEVEL", "$batteryLevel %")
                    }
                    Rule()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("CPU CLOCK", cpuClock)
                        Metric("GPU CLOCK", gpuClock)
                    }
                    Rule()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("MEMORY CLOCK", memoryClock)
                        Metric("GPU SENSOR", "N/A")
                    }
                    Text("Valores ilustrativos: FPS, frametime e clocks não são medições reais do jogo ou do hardware.", color = Color(0xFF27302C), fontSize = 9.sp)
                    Text("N/A = sensor/dado não disponibilizado pelo Android", color = Color(0xFF27302C), fontSize = 10.sp)
                }
            }
            item { Graph("CPU USAGE HISTORY", green, cpuHistory) }
            item { Graph("RAM USAGE HISTORY", cyan, ramHistory) }
            item { Text("AFTERBURNER MOBILE • ANDROID EDITION • v0.9", color = Color.Gray, fontSize = 9.sp) }
        }
        }
    }
}

@Composable
private fun Header() {
    Row(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(metal, Color(0xFF68736D))), RoundedCornerShape(8.dp))
            .border(2.dp, Color(0xFF3D4842), RoundedCornerShape(8.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.afterburner_logo),
            contentDescription = "MSI Afterburner original logo",
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text("MSI AFTERBURNER", color = Color(0xFF17201B), fontSize = 19.sp, fontWeight = FontWeight.Black)
            Text("MOBILE / HARDWARE MONITOR", color = Color(0xFF29332D), fontSize = 9.sp)
        }
    }
}

@Composable
private fun Gauge(title: String, value: String, unit: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Brush.verticalGradient(listOf(Color(0xFF39423D), Color(0xFF171D1A))), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF65716A), RoundedCornerShape(6.dp)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color(0xFFCFD7D1), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Canvas(Modifier.size(88.dp)) {
            val stroke = 8.dp.toPx()
            drawArc(Color(0xFF59645D), 135f, 270f, false, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(accent, 135f, value.toFloatOrNull()?.coerceIn(0f, 100f)?.times(2.7f) ?: 5f, false,
                style = Stroke(stroke, cap = StrokeCap.Round))
            val cx = size.width / 2
            val cy = size.height / 2
            val a = Math.toRadians(135.0)
            drawLine(accent, Offset(cx, cy), Offset(cx + cos(a).toFloat() * 24.dp.toPx(), cy + sin(a).toFloat() * 24.dp.toPx()), 2.dp.toPx())
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Color(0xFFFFB347), fontSize = 21.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(" $unit", color = accent, fontSize = 9.sp)
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFF3A443E), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color(0xFF9A5700), fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Rule() { Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF68736C))) }

@Composable
private fun Graph(title: String, color: Color, samples: List<Float>) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF252D28), RoundedCornerShape(6.dp))
        .border(1.dp, Color(0xFF59645D), RoundedCornerShape(6.dp)).padding(10.dp)) {
        Text(title, color = Color(0xFFD4DDD6), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Canvas(Modifier.fillMaxWidth().height(74.dp).padding(top = 6.dp)) {
            for (i in 0..4) {
                val y = size.height * i / 4f
                drawLine(Color(0xFF46534A), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            if (samples.size > 1) {
                val pts = samples.mapIndexed { index, sample ->
                    val x = size.width * index / (samples.size - 1).toFloat()
                    val y = size.height * (1f - sample.coerceIn(0f, 100f) / 100f)
                    Offset(x, y)
                }
                for (i in 0 until pts.lastIndex) drawLine(color, pts[i], pts[i + 1], 2.dp.toPx())
            }
        }
        Text("ÚLTIMAS ${samples.size} LEITURAS • %", color = Color(0xFF849188), fontSize = 8.sp)
    }
}


private fun readCpuCounters(): Pair<Long, Long>? {
    return try {
        val fields = File("/proc/stat").bufferedReader().use { it.readLine() }
            ?.trim()?.split(Regex("\\s+")) ?: return null
        if (fields.firstOrNull() != "cpu" || fields.size < 5) return null
        val values = fields.drop(1).take(8).map { it.toLongOrNull() ?: 0L }
        val total = values.sum()
        val idle = values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L }
        total to idle
    } catch (_: Exception) {
        null
    }
}
