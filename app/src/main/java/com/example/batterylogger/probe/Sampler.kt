package com.example.batterylogger.probe

import android.content.Context
import android.os.Process
import android.os.SystemClock
import com.example.batterylogger.io.CsvAppender
import com.example.batterylogger.service.LoggerService
import com.example.batterylogger.session.Session

/**
 * Один замер = одна строка samples.csv.
 * Время фиксируется ПЕРВЫМ, до любых binder-вызовов, чтобы не смещать actual_*.
 */
object Sampler {

    /**
     * @param slot    номер слота сетки (-1, если неизвестен)
     * @param planned плановое elapsedRealtime слота
     * @param via     чем был запланирован этот тик: start / handler / exact_allow_idle / inexact_allow_idle
     */
    fun writeSample(ctx: Context, s: Session, slot: Long, planned: Long, via: String) {
        val actualElapsed = SystemClock.elapsedRealtime()
        val actualUptime = SystemClock.uptimeMillis()
        val actualWall = System.currentTimeMillis()

        val row = ArrayList<Pair<String, String>>(40)
        row += "session_id" to s.id
        row += "mechanism" to s.mechanism.name
        row += "scheduled_via" to via
        row += "slot_index" to slot.toString()
        row += "planned_elapsed_ms" to planned.toString()
        // Плановое wall-время не храним как отдельную величину: оно выводится из t0_wall паспорта,
        // а wall clock может прыгать (NTP, смена часового пояса) — поэтому сетка по elapsed.
        row += "actual_elapsed_ms" to actualElapsed.toString()
        row += "actual_uptime_ms" to actualUptime.toString() // Δelapsed − Δuptime = время в глубоком сне
        row += "actual_wall_ms" to actualWall.toString()
        row += "lateness_ms" to (if (slot >= 0) actualElapsed - planned else "NA").toString()
        // Жив ли экземпляр сервиса в ЭТОМ процессе. false в механизме B = процесс был убит,
        // а будильник разбудил новый процесс без FGS.
        row += "fgs_alive" to LoggerService.alive.toString()
        row += "pid" to Process.myPid().toString()
        row += "proc_start_elapsed_ms" to Process.getStartElapsedRealtime().toString()

        BatteryProbe.read(ctx, row)
        PowerProbe.read(ctx, row)

        CsvAppender.append(s.samplesFile, row)
    }
}
