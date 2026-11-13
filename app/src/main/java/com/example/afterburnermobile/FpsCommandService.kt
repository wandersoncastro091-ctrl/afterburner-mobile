package com.example.afterburnermobile

import android.os.SystemClock
import kotlin.math.roundToInt

class FpsCommandService : IFpsCommand.Stub() {
    @Volatile private var initialized = false
    @Volatile private var lastResetMs = 0L

    override fun readFps(): Int {
        val now = SystemClock.elapsedRealtime()
        if (!initialized || now - lastResetMs >= RESET_INTERVAL_MS) {
            val enabled = runCommand("dumpsys SurfaceFlinger --timestats -clear -enable")
            if (enabled == null) return -1
            initialized = true
            lastResetMs = now
            return -1
        }

        val output = runCommand("dumpsys SurfaceFlinger --timestats -dump") ?: return -1
        return AVERAGE_FPS_REGEX.find(output)
            ?.groupValues?.getOrNull(1)
            ?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it > 0f }
            ?.roundToInt()
            ?: -1
    }

    private fun runCommand(command: String): String? = try {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor() == 0) output else null
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val RESET_INTERVAL_MS = 3000L
        val AVERAGE_FPS_REGEX = Regex("averageFPS\\s*=\\s*([0-9.]+)")
    }
}
