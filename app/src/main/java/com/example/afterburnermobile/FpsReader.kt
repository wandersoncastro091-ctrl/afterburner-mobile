package com.example.afterburnermobile

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class FpsReader(context: Context) {
    private val appContext = context.applicationContext
    @Volatile private var command: IFpsCommand? = null
    @Volatile private var binding = false
    private var connection: ServiceConnection? = null

    @Synchronized
    fun connect() {
        if (command != null || binding) return
        try {
            if (!Shizuku.pingBinder() ||
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
            ) return

            val args = Shizuku.UserServiceArgs(
                ComponentName(appContext.packageName, FpsCommandService::class.java.name)
            ).daemon(true)
                .tag("afterburner-fps-reader")
                .version(1)
                .processNameSuffix("fps")

            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    command = IFpsCommand.Stub.asInterface(service)
                    binding = false
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    command = null
                    binding = false
                }
            }
            connection = serviceConnection
            binding = true
            Shizuku.bindUserService(args, serviceConnection)
        } catch (_: Exception) {
            binding = false
            command = null
        }
    }

    suspend fun readFps(): Int? = withContext(Dispatchers.IO) {
        val runner = command ?: return@withContext null
        runCatching { runner.readFps().takeIf { it > 0 } }.getOrNull()
    }

    @Synchronized
    fun close() {
        val current = connection
        if (current != null) {
            try {
                val args = Shizuku.UserServiceArgs(
                    ComponentName(appContext.packageName, FpsCommandService::class.java.name)
                ).daemon(true)
                    .tag("afterburner-fps-reader")
                    .version(1)
                    .processNameSuffix("fps")
                Shizuku.unbindUserService(args, current, true)
            } catch (_: Exception) {}
        }
        connection = null
        command = null
        binding = false
    }
}
