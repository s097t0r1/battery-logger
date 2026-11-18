package com.example.batterylogger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import com.example.batterylogger.io.DevicePassport
import com.example.batterylogger.io.EventLog
import com.example.batterylogger.io.StorageDirs
import com.example.batterylogger.probe.Sampler
import com.example.batterylogger.schedule.AlarmScheduler
import com.example.batterylogger.schedule.HandlerScheduler
import com.example.batterylogger.session.Mechanism
import com.example.batterylogger.session.Session
import com.example.batterylogger.session.SessionStore
import com.example.batterylogger.ui.MainActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service типа connectedDevice.
 *
 * FGS держится в ОБОИХ механизмах — так у процесса одинаковый приоритет
 * (не попадает в cached/freezer), и между A и B различается только таймер.
 *
 * START_NOT_STICKY: если система убьёт процесс, мы НЕ просим её перезапустить сервис —
 * гибель должна остаться видимой в данных (process_exits.csv, разрыв в samples.csv).
 * Если хотите измерять «с перезапуском», это отдельное условие эксперимента: START_STICKY.
 */
class LoggerService : Service() {

    companion object {
        const val ACTION_START = "com.example.batterylogger.START"
        const val ACTION_STOP = "com.example.batterylogger.STOP"
        const val EXTRA_MECHANISM = "mechanism"
        const val INTERVAL_MS = 60_000L

        const val CHANNEL_ID = "logger"
        private const val NOTIF_ID = 1

        /** Жив ли экземпляр сервиса в текущем процессе (пишется в каждую строку). */
        @Volatile var alive = false
            private set

        fun createChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            // IMPORTANCE_LOW: без звука и всплывания. Уведомление НИКОГДА не обновляется —
            // каждое обновление = лишняя работа системы.
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Логгер", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private var session: Session? = null
    private var thread: HandlerThread? = null
    private var handlerScheduler: HandlerScheduler? = null
    private var stopRequested = false

    override fun onCreate() {
        super.onCreate()
        alive = true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // startForeground — первым делом, до любой работы (требование startForegroundService).
                goForeground()
                val m = Mechanism.valueOf(intent.getStringExtra(EXTRA_MECHANISM) ?: Mechanism.HANDLER.name)
                startSession(m)
            }
            ACTION_STOP -> stopSession()
            else -> stopSelf() // неожиданный запуск без действия
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Логгер батареи работает")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        // На Android 14+ без FOREGROUND_SERVICE_CONNECTED_DEVICE и предусловия (CHANGE_WIFI_STATE)
        // здесь будет SecurityException.
        startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
    }

    private fun startSession(m: Mechanism) {
        if (session != null) return // уже идёт в этом процессе

        // Сессия могла «осиротеть»: процесс убит, а prefs и будильник остались.
        SessionStore.load(this)?.let { old ->
            AlarmScheduler.cancel(this)
            EventLog.write(old, "SESSION_SUPERSEDED", "new session started")
        }

        val now = SystemClock.elapsedRealtime()
        val id = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "_" + m.name.lowercase()
        val dir = File(StorageDirs.root(this), "sessions/$id").apply { mkdirs() }
        val s = Session(id, dir, now, INTERVAL_MS, m, SessionStore.currentBootCount(this))
        session = s
        SessionStore.save(this, s)

        DevicePassport.write(this, s)
        EventLog.write(s, "SESSION_START", m.name)

        when (m) {
            Mechanism.HANDLER -> {
                val t = HandlerThread("logger").apply { start() }
                thread = t
                handlerScheduler = HandlerScheduler(Handler(t.looper), s) { slot, planned, via ->
                    Sampler.writeSample(this, s, slot, planned, via)
                }.also { it.start() }
            }
            Mechanism.ALARM -> {
                Sampler.writeSample(this, s, 0, s.t0Elapsed, "start")
                AlarmScheduler.scheduleNext(this, s)
            }
        }
    }

    private fun stopSession() {
        stopRequested = true
        // Сессию берём и из памяти, и из prefs: стоп мог прийти в новый процесс,
        // где сервиса не было, а будильники B ещё стоят.
        val s = session ?: SessionStore.load(this)
        handlerScheduler?.stop()
        thread?.quitSafely()
        AlarmScheduler.cancel(this)
        s?.let { EventLog.write(it, "SESSION_STOP", "user") }
        SessionStore.clear(this)
        session = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Уничтожение без запроса пользователя (например, кнопка Stop в Task Manager).
        // Убийство процесса сюда НЕ попадает — оно видно в process_exits.csv.
        if (!stopRequested) {
            session?.let { EventLog.write(it, "FGS_DESTROYED_UNEXPECTEDLY") }
            handlerScheduler?.stop()
            thread?.quitSafely()
        }
        alive = false
        super.onDestroy()
    }
}
