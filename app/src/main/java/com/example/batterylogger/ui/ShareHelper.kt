package com.example.batterylogger.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.batterylogger.io.LogFileProvider
import com.example.batterylogger.io.StorageDirs
import com.example.batterylogger.session.SessionStore
import java.io.File

/**
 * Собирает интент «Поделиться»: текст с моделью телефона + файлы последней сессии.
 * Вызывается только по нажатию кнопки — в фоне ничего не делает.
 */
object ShareHelper {

    /** Активная сессия, иначе самая свежая папка (имена начинаются с yyyyMMdd_HHmmss). */
    fun latestSessionDir(ctx: Context): File? {
        SessionStore.load(ctx)?.let { return it.dir }
        return File(StorageDirs.root(ctx), "sessions")
            .listFiles { f -> f.isDirectory }
            ?.maxByOrNull { it.name }
    }

    /** null — делиться нечем. */
    fun buildIntent(ctx: Context): Intent? {
        val dir = latestSessionDir(ctx) ?: return null
        val files = listOf("passport.csv", "samples.csv", "events.csv")
            .map { File(dir, it) }
            .filter { it.isFile } +
            // Общий для всех сессий файл причин гибели процесса — тоже нужен для анализа.
            listOf(File(StorageDirs.root(ctx), "process_exits.csv")).filter { it.isFile }
        if (files.isEmpty()) return null

        val active = SessionStore.load(ctx)?.dir?.canonicalPath == dir.canonicalPath
        val samples = File(dir, "samples.csv")
        // Подсчёт строк — только по нажатию, файл небольшой (≈1 строка в минуту).
        val rows = if (samples.isFile) (samples.useLines { it.count() } - 1).coerceAtLeast(0) else 0

        val text = buildString {
            append("Battery Logger\n")
            append("Устройство: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
            append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), сборка ${Build.DISPLAY}\n")
            append("Сессия: ${dir.name}${if (active) " — ещё идёт, файл неполный" else ""}\n")
            append("Строк в samples.csv: $rows\n")
            append("Файлы: ${files.joinToString { LogFileProvider.displayName(ctx, it) }}")
        }

        val uris = ArrayList<Uri>(files.map { LogFileProvider.uriFor(ctx, it) })
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Battery Logger: ${Build.MODEL}, ${dir.name}")
            putExtra(Intent.EXTRA_TEXT, text)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            // ClipData + флаг — так право на чтение URI получает выбранное приложение.
            clipData = ClipData.newRawUri(null, uris[0]).also { c ->
                uris.drop(1).forEach { c.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Отправить логи")
    }
}
