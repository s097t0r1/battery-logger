package com.example.batterylogger.session

import android.content.Context
import android.provider.Settings
import java.io.File

/** Механизм пробуждения. Пишется в каждую строку и в паспорт. */
enum class Mechanism {
    /** A: Handler.postDelayed. Отсчёт идёт по uptimeMillis, который стоит в глубоком сне. */
    HANDLER,

    /** B: AlarmManager.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP). */
    ALARM
}

/**
 * Параметры одной сессии измерений.
 *
 * Плановое время слота считается по фиксированной сетке t0 + n * interval,
 * а НЕ от фактического времени предыдущего срабатывания — иначе дрейф накапливался бы
 * и терялся. Пропущенные слоты видны как разрывы в slot_index (считаются при анализе).
 */
data class Session(
    val id: String,
    val dir: File,
    val t0Elapsed: Long,
    val intervalMs: Long,
    val mechanism: Mechanism,
    val bootCount: Int
) {
    val samplesFile: File get() = File(dir, "samples.csv")
    val eventsFile: File get() = File(dir, "events.csv")
    val passportFile: File get() = File(dir, "passport.csv")

    fun planned(slot: Long): Long = t0Elapsed + slot * intervalMs

    /** Первый слот сетки, плановое время которого строго позже now. */
    fun firstSlotAfter(nowElapsed: Long): Long =
        ((nowElapsed - t0Elapsed).coerceAtLeast(0) / intervalMs) + 1
}

/**
 * Хранение активной сессии в SharedPreferences.
 * Нужно потому, что в механизме B будильник может разбудить уже НОВЫЙ процесс
 * (старый убит), и приёмнику надо знать, куда писать.
 * Пишется только при старте/стопе — на каждом тике диск не трогаем.
 */
object SessionStore {
    private const val PREFS = "session"

    fun save(ctx: Context, s: Session) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("id", s.id)
            .putString("dir", s.dir.absolutePath)
            .putLong("t0", s.t0Elapsed)
            .putLong("interval", s.intervalMs)
            .putString("mechanism", s.mechanism.name)
            .putInt("boot", s.bootCount)
            .commit() // синхронно: старт сессии — редкое событие, потерять его нельзя
    }

    /**
     * Возвращает сессию или null. Если устройство перезагружалось, elapsedRealtime обнулился,
     * сетка сессии недействительна — такую сессию считаем завершённой.
     */
    fun load(ctx: Context): Session? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = p.getString("id", null) ?: return null
        val boot = p.getInt("boot", -1)
        if (boot != currentBootCount(ctx)) return null
        return Session(
            id = id,
            dir = File(p.getString("dir", "")!!),
            t0Elapsed = p.getLong("t0", 0),
            intervalMs = p.getLong("interval", 60_000),
            mechanism = Mechanism.valueOf(p.getString("mechanism", Mechanism.HANDLER.name)!!),
            bootCount = boot
        )
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Settings.Global.BOOT_COUNT (API 24+): номер загрузки устройства. */
    fun currentBootCount(ctx: Context): Int =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1)
}
