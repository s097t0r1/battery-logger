package com.example.batterylogger

import android.app.Application
import com.example.batterylogger.io.ExitInfoDumper
import com.example.batterylogger.service.LoggerService

class LoggerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LoggerService.createChannel(this)
        // Срабатывает при каждом рождении процесса — в т.ч. после убийства системой.
        runCatching { ExitInfoDumper.dump(this) }
    }
}
