package com.example.afterburnermobile

import android.content.Context
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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
    var batteryTemp by remember { mutableStateOf("--.-") }
    var batteryLevel by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            val rt = Runtime.getRuntime()
            ram = (((rt.totalMemory() - rt.freeMemory()).toDouble() / rt.maxMemory()) * 100).toInt().coerceIn(0, 100)
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
        LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        Gauge("CPU USAGE", "N/A", "%", green, Modifier.weight(1f))
                        Gauge("RAM USAGE", "$ram", "%", cyan, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Gauge("FRAMERATE", "N/A", "FPS", green, Modifier.weight(1f))
                        Gauge("FRAMETIME", "N/A", "ms", cyan, Modifier.weight(1f))
                    }
                    Rule()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("BATTERY TEMP", "$batteryTemp °C")
                        Metric("BATTERY LEVEL", "$batteryLevel %")
                    }
                    Rule()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("CORE CLOCK", "N/A")
                        Metric("MEMORY CLOCK", "N/A")
                    }
                    Text("N/A = sensor/data not exposed by Android", color = Color(0xFF27302C), fontSize = 10.sp)
                }
            }
            item { Graph("GPU TEMPERATURE", cyan) }
            item { Graph("GPU USAGE", green) }
            item { Text("AFTERBURNER MOBILE • ANDROID EDITION • v0.1", color = Color.Gray, fontSize = 9.sp) }
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
        Text("MSI", color = Color(0xFF202823), fontSize = 25.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(10.dp))
        Column {
            Text("AFTERBURNER", color = Color(0xFF17201B), fontSize = 19.sp, fontWeight = FontWeight.Black)
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
            Text(value, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(" $unit", color = accent, fontSize = 9.sp)
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFF3A443E), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color(0xFF111A15), fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Rule() { Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF68736C))) }

@Composable
private fun Graph(title: String, color: Color) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF252D28), RoundedCornerShape(6.dp))
        .border(1.dp, Color(0xFF59645D), RoundedCornerShape(6.dp)).padding(10.dp)) {
        Text(title, color = Color(0xFFD4DDD6), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Canvas(Modifier.fillMaxWidth().height(74.dp).padding(top = 6.dp)) {
            for (i in 0..4) {
                val y = size.height * i / 4f
                drawLine(Color(0xFF46534A), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val pts = (0..40).map { i -> Offset(size.width * i / 40f, size.height * (0.48f + 0.08f * sin(i * 0.45f))) }
            for (i in 0 until pts.lastIndex) drawLine(color, pts[i], pts[i + 1], 2.dp.toPx())
        }
        Text("SENSOR HISTORY — PLACEHOLDER", color = Color(0xFF849188), fontSize = 8.sp)
    }
}
