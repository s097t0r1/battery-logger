package com.example.batterylogger.io

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.example.batterylogger.probe.BatteryProbe
import com.example.batterylogger.session.Session
import java.io.File
import java.util.TimeZone

/**
 * passport.csv (key,value) — один раз при старте сессии.
 *
 * Версия оболочки (One UI / HyperOS) публичным API не отдаётся. Чтение системных свойств
 * из приложения — non-SDK интерфейс, его не используем. Снимайте при подготовке устройства:
 *   adb shell getprop > props_<device>.txt
 */
object DevicePassport {

    fun write(ctx: Context, s: Session) {
        val f = s.passportFile
        if (f.exists()) f.delete()
        fields(ctx, s).forEach { (k, v) ->
            CsvAppender.append(f, listOf("key" to k, "value" to v))
        }
    }

    @Suppress("DEPRECATION")
    private fun fields(ctx: Context, s: Session): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        out += "manufacturer" to Build.MANUFACTURER
        out += "brand" to Build.BRAND
        out += "model" to Build.MODEL
        out += "device" to Build.DEVICE
        out += "product" to Build.PRODUCT
        out += "hardware" to Build.HARDWARE
        if (Build.VERSION.SDK_INT >= 31) {
            out += "soc_manufacturer" to Build.SOC_MANUFACTURER
            out += "soc_model" to Build.SOC_MODEL
        }
        out += "sdk_int" to Build.VERSION.SDK_INT.toString()
        out += "release" to Build.VERSION.RELEASE
        out += "security_patch" to Build.VERSION.SECURITY_PATCH
        out += "incremental" to Build.VERSION.INCREMENTAL
        out += "build_display" to Build.DISPLAY // у многих OEM содержит версию прошивки
        out += "fingerprint" to Build.FINGERPRINT
        out += "oem_shell_version" to "not_available_via_public_api; use adb getprop"

        val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        out += "app_version" to (pkg.versionName ?: "")
        out += "app_target_sdk" to ctx.applicationInfo.targetSdkVersion.toString()

        out += "session_id" to s.id
        out += "mechanism" to s.mechanism.name
        out += "interval_ms" to s.intervalMs.toString()
        out += "t0_elapsed_ms" to s.t0Elapsed.toString()
        out += "t0_wall_ms" to System.currentTimeMillis().toString()
        out += "t0_uptime_ms" to SystemClock.uptimeMillis().toString()
        out += "boot_count" to s.bootCount.toString()
        out += "timezone" to TimeZone.getDefault().id
        out += "fsync_each_line" to CsvAppender.FSYNC_EACH_LINE.toString()
        out += "storage_external" to StorageDirs.isExternal(ctx).toString()
        out += "storage_path" to s.dir.absolutePath

        // Условия старта, влияющие на интерпретацию.
        out += "post_notifications_granted" to if (Build.VERSION.SDK_INT >= 33)
            (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED).toString()
        else "NA"
        out += "notifications_enabled" to
            ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled().toString()
        out += "can_exact_alarm" to if (Build.VERSION.SDK_INT >= 31)
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms().toString()
        else "NA"
        out += "ignoring_batt_opt" to ctx.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(ctx.packageName).toString()
        out += "standby_bucket" to ctx.getSystemService(UsageStatsManager::class.java).appStandbyBucket.toString()

        out += BatteryProbe.passportFields(ctx)
        return out
    }
}
