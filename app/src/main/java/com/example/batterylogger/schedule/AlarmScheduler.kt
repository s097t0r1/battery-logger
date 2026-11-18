package com.example.batterylogger.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.example.batterylogger.io.EventLog
import com.example.batterylogger.session.Session

/**
 * Механизм B. Каждый тик ставит ОДИН будильник на следующий слот сетки.
 *
 * Ограничения платформы (это данные, не баг):
 * - В Doze будильники *AndAllowWhileIdle срабатывают не чаще ~1 раза в 9 минут на приложение.
 * - На Android 14+ SCHEDULE_EXACT_ALARM по умолчанию не выдан (targetSdk >= 33);
 *   UI не даёт стартовать B без него.
 * - Если разрешение отозвали посреди сессии, переходим на setAndAllowWhileIdle
 *   (неточный) и фиксируем это: событие в events.csv + scheduled_via в каждой строке.
 *   Если для методики нужно «падать», а не деградировать, — замените catch на остановку сессии.
 */
object AlarmScheduler {
    const val EXTRA_SLOT = "slot"
    const val EXTRA_VIA = "via"
    private const val REQUEST_CODE = 1

    /** Последний способ планирования в этом процессе — чтобы писать событие только при смене. */
    @Volatile private var lastVia: String? = null

    fun scheduleNext(ctx: Context, s: Session) {
        val app = ctx.applicationContext
        val am = app.getSystemService(AlarmManager::class.java)
        val now = SystemClock.elapsedRealtime()
        val slot = s.firstSlotAfter(now)
        val at = s.planned(slot)

        var via = "exact_allow_idle"
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                throw SecurityException("canScheduleExactAlarms=false")
            }
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(app, slot, via))
        } catch (e: SecurityException) {
            via = "inexact_allow_idle"
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(app, slot, via))
            if (lastVia != via) EventLog.write(s, "EXACT_ALARM_UNAVAILABLE", e.message ?: "")
        }
        if (lastVia != null && lastVia != via && via == "exact_allow_idle") {
            EventLog.write(s, "EXACT_ALARM_RESTORED")
        }
        lastVia = via
    }

    fun cancel(ctx: Context) {
        val app = ctx.applicationContext
        val pi = PendingIntent.getBroadcast(
            app, REQUEST_CODE, Intent(app, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        app.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
        lastVia = null
    }

    private fun pending(ctx: Context, slot: Long, via: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQUEST_CODE,
            Intent(ctx, AlarmReceiver::class.java)
                .putExtra(EXTRA_SLOT, slot)
                .putExtra(EXTRA_VIA, via),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
