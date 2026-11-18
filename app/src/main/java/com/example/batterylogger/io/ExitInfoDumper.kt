package com.example.batterylogger.io

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File

/**
 * При каждом старте процесса (в т.ч. когда будильник будит «новый» процесс)
 * дописывает в process_exits.csv новые записи ApplicationExitInfo (API 30+).
 * Это ответ на вопрос «кто и почему убил логгер»: система, OEM, пользователь.
 * Файл общий для всех сессий; сопоставление — по wall-времени.
 */
object ExitInfoDumper {
    private const val PREFS = "exit_info"

    fun dump(ctx: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        val am = ctx.getSystemService(ActivityManager::class.java)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastTs = prefs.getLong("last_ts", 0)

        val fresh = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 16)
            .filter { it.timestamp > lastTs }
            .sortedBy { it.timestamp }
        if (fresh.isEmpty()) return

        val f = File(StorageDirs.root(ctx), "process_exits.csv")
        for (e in fresh) {
            CsvAppender.append(
                f,
                listOf(
                    "exit_wall_ms" to e.timestamp.toString(),
                    "pid" to e.pid.toString(),
                    "reason" to e.reason.toString(),
                    "reason_name" to reasonName(e.reason),
                    "status" to e.status.toString(),
                    "importance" to e.importance.toString(),
                    "pss_kb" to e.pss.toString(),
                    "rss_kb" to e.rss.toString(),
                    "description" to (e.description ?: "")
                )
            )
        }
        prefs.edit().putLong("last_ts", fresh.last().timestamp).apply()
    }

    /** Имена только для констант API 30; новые коды пишутся как code_N (сырое значение есть в reason). */
    private fun reasonName(r: Int): String = when (r) {
        ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "code_$r"
    }
}
