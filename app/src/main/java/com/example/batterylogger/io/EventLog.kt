package com.example.batterylogger.io

import android.os.Process
import android.os.SystemClock
import com.example.batterylogger.session.Session

/** События сессии (старт, стоп, смена способа планирования и т.п.) в events.csv. */
object EventLog {
    fun write(s: Session, event: String, detail: String = "") {
        CsvAppender.append(
            s.eventsFile,
            listOf(
                "wall_ms" to System.currentTimeMillis().toString(),
                "elapsed_ms" to SystemClock.elapsedRealtime().toString(),
                "pid" to Process.myPid().toString(),
                "event" to event,
                "detail" to detail
            )
        )
    }
}
