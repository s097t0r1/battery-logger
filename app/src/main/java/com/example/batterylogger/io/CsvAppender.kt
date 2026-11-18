package com.example.batterylogger.io

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * Дозапись строк CSV.
 *
 * Компромисс по буферизации (см. обсуждение):
 * - Каждая строка сразу уходит в write(2) — FileOutputStream не буферизует.
 *   После write() данные в page cache ядра и ПЕРЕЖИВАЮТ убийство процесса.
 *   Теряются только при kernel panic / внезапном отключении питания.
 * - Буфер в памяти приложения сэкономил бы копейки (CPU уже проснулся ради замера),
 *   но терялся бы целиком при kill — поэтому его нет.
 * - fsync выключен по умолчанию: он заставляет флеш-память писать физически сейчас.
 *   Включите FSYNC_EACH_LINE, если важна устойчивость к выключению питания.
 */
object CsvAppender {
    const val FSYNC_EACH_LINE = false

    private val lock = Any()

    /** Заголовок пишется, если файл новый; порядок колонок задаёт порядок пар. */
    fun append(file: File, row: List<Pair<String, String>>) {
        synchronized(lock) {
            val isNew = !file.exists() || file.length() == 0L
            val sb = StringBuilder(512)
            if (isNew) {
                row.joinTo(sb, ",") { esc(it.first) }
                sb.append('\n')
            }
            row.joinTo(sb, ",") { esc(it.second) }
            sb.append('\n')
            file.parentFile?.mkdirs()
            FileOutputStream(file, true).use { fos ->
                fos.write(sb.toString().toByteArray(Charsets.UTF_8))
                if (FSYNC_EACH_LINE) fos.fd.sync()
            }
        }
    }

    private fun esc(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"" + s.replace("\"", "\"\"") + "\""
        else s
}

/** Корень хранилища: /sdcard/Android/data/<pkg>/files — доступен через adb pull. */
object StorageDirs {
    fun root(ctx: Context): File = ctx.getExternalFilesDir(null) ?: ctx.filesDir

    fun isExternal(ctx: Context): Boolean = ctx.getExternalFilesDir(null) != null
}
