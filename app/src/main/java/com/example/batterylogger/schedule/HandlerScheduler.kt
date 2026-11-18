package com.example.batterylogger.schedule

import android.os.Handler
import android.os.SystemClock
import com.example.batterylogger.session.Session

/**
 * Механизм A. Handler.postDelayed отсчитывает задержку по SystemClock.uptimeMillis(),
 * который НЕ идёт, пока CPU в глубоком сне. Поэтому при выключенном экране тик
 * сработает только тогда, когда устройство разбудит кто-то другой.
 * Это и измеряется: lateness_ms и разрывы slot_index.
 *
 * Задержку считаем от сетки в elapsedRealtime, а не «60 с от прошлого тика».
 */
class HandlerScheduler(
    private val handler: Handler,
    private val session: Session,
    private val onTick: (slot: Long, planned: Long, via: String) -> Unit
) {
    private var nextSlot = 0L

    private val tick = object : Runnable {
        override fun run() {
            val slot = nextSlot
            onTick(slot, session.planned(slot), if (slot == 0L) "start" else "handler")
            scheduleNext()
        }
    }

    fun start() {
        nextSlot = 0
        handler.post(tick) // слот 0 — сразу при старте
    }

    fun stop() {
        handler.removeCallbacks(tick)
    }

    private fun scheduleNext() {
        val now = SystemClock.elapsedRealtime()
        nextSlot = session.firstSlotAfter(now) // пропущенные слоты не догоняем — они станут разрывом
        val delay = (session.planned(nextSlot) - now).coerceAtLeast(0)
        handler.postDelayed(tick, delay)
    }
}
