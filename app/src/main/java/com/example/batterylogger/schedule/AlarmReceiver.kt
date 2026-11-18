package com.example.batterylogger.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.batterylogger.probe.Sampler
import com.example.batterylogger.session.Mechanism
import com.example.batterylogger.session.SessionStore

/**
 * Срабатывание будильника механизма B.
 *
 * Система держит wake lock только на время onReceive(), поэтому вся работа
 * (несколько binder-вызовов + одна строка в файл, единицы миллисекунд) делается
 * СИНХРОННО здесь. Никаких потоков и goAsync — иначе CPU может уснуть до записи.
 *
 * Сервис намеренно НЕ перезапускаем, если процесс был убит: это изменило бы условия.
 * Такие строки видны по fgs_alive=false и смене pid.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val s = SessionStore.load(app) ?: return // сессия остановлена или была перезагрузка
        if (s.mechanism != Mechanism.ALARM) return

        val slot = intent.getLongExtra(AlarmScheduler.EXTRA_SLOT, -1)
        val via = intent.getStringExtra(AlarmScheduler.EXTRA_VIA) ?: "unknown"
        Sampler.writeSample(app, s, slot, if (slot >= 0) s.planned(slot) else -1, via)
        AlarmScheduler.scheduleNext(app, s)
    }
}
