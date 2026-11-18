package com.example.batterylogger.io

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/**
 * Минимальный read-only провайдер для отправки логов через шторку «Поделиться».
 *
 * Почему не androidx FileProvider: проект намеренно без зависимостей (см. app/build.gradle.kts),
 * а для отдачи нескольких CSV на чтение хватает ~60 строк.
 *
 * Безопасность:
 * - exported=false + grantUriPermissions=true: доступ есть только у приложения,
 *   которому мы явно выдали FLAG_GRANT_READ_URI_PERMISSION на конкретный URI;
 * - только режим "r"; insert/update/delete запрещены;
 * - путь нормализуется (canonicalFile) и обязан лежать внутри корня логов —
 *   защита от "../" в URI.
 */
class LogFileProvider : ContentProvider() {

    companion object {
        fun authority(ctx: Context) = "${ctx.packageName}.logs"

        /** content://<pkg>.logs/<путь относительно корня логов> */
        fun uriFor(ctx: Context, file: File): Uri {
            val root = StorageDirs.root(ctx).canonicalFile
            val rel = file.canonicalFile.relativeTo(root).invariantSeparatorsPath
            val b = Uri.Builder().scheme("content").authority(authority(ctx))
            rel.split('/').forEach { b.appendPath(it) }
            return b.build()
        }

        /**
         * Имя файла, которое увидит получатель. С моделью и id сессии, чтобы файлы
         * с разных телефонов не путались в чате/почте (у всех иначе будет "samples.csv").
         * sessions/20261022_101500_alarm/samples.csv → Pixel_9a_20261022_101500_alarm_samples.csv
         */
        fun displayName(ctx: Context, file: File): String {
            val root = StorageDirs.root(ctx).canonicalFile
            val rel = file.canonicalFile.relativeTo(root).invariantSeparatorsPath
                .removePrefix("sessions/")
                .replace('/', '_')
            val model = Build.MODEL.replace(Regex("[^A-Za-z0-9._-]+"), "_")
            return "${model}_$rel"
        }
    }

    override fun onCreate(): Boolean = true

    private fun resolve(uri: Uri): File {
        val ctx = context ?: throw IllegalStateException("no context")
        val root = StorageDirs.root(ctx).canonicalFile
        val f = File(root, uri.path ?: "").canonicalFile
        if (!f.path.startsWith(root.path + File.separator) || !f.isFile) {
            throw SecurityException("Недопустимый путь: $uri")
        }
        return f
    }

    override fun getType(uri: Uri): String = "text/csv"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Только чтение")
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /** Получатели (почта, мессенджеры) спрашивают имя и размер через query(). */
    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        val f = resolve(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val values = cols.map {
            when (it) {
                OpenableColumns.DISPLAY_NAME -> displayName(context!!, f)
                OpenableColumns.SIZE -> f.length()
                else -> null
            }
        }
        return MatrixCursor(cols, 1).apply { addRow(values) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = throw UnsupportedOperationException()
}
