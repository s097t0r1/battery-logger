package com.example.batterylogger.probe

import android.app.AlarmManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.view.Display

/**
 * Состояние питания/экрана. "NA" означает, что на этой версии Android API нет
 * (это не подстановка значения, а отсутствие источника).
 */
object PowerProbe {

    fun read(ctx: Context, row: MutableList<Pair<String, String>>) {
        val app = ctx.applicationContext
        val pm = app.getSystemService(PowerManager::class.java)

        // isInteractive и состояние дисплея расходятся при AOD — пишем оба.
        row += "interactive" to pm.isInteractive.toString()
        val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        // Display.STATE_*: 1 OFF, 2 ON, 3 DOZE, 4 DOZE_SUSPEND, 5 VR, 6 ON_SUSPEND (сырое int).
        row += "display_state" to (display?.state?.toString() ?: "no_display")

        row += "device_idle" to pm.isDeviceIdleMode.toString()
        // isDeviceLightIdleMode публичен с API 33; в reference помечен устаревшим в более
        // поздней версии — значение всё равно пишем, но интерпретируйте осторожно.
        @Suppress("DEPRECATION")
        row += "light_idle" to if (Build.VERSION.SDK_INT >= 33) pm.isDeviceLightIdleMode.toString() else "NA"
        row += "power_save" to pm.isPowerSaveMode.toString()
        row += "ignoring_batt_opt" to pm.isIgnoringBatteryOptimizations(app.packageName).toString()
        row += "thermal_status" to pm.currentThermalStatus.toString() // API 29

        // Бакет App Standby (API 28): 10 ACTIVE, 20 WORKING_SET, 30 FREQUENT, 40 RARE, 45 RESTRICTED.
        val usm = app.getSystemService(UsageStatsManager::class.java)
        row += "standby_bucket" to usm.appStandbyBucket.toString()

        val am = app.getSystemService(AlarmManager::class.java)
        row += "can_exact_alarm" to if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms().toString() else "NA"
    }
}
