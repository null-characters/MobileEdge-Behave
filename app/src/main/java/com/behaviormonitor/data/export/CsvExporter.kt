package com.behaviormonitor.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.behaviormonitor.domain.model.StateEvent
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV 导出工具
 * 格式：timestamp,from_state,to_state,confidence
 */
object CsvExporter {

    private val csvDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * 导出事件列表为 CSV 文件
     * @return 导出文件路径
     */
    fun export(context: Context, events: List<StateEvent>, date: String): String {
        val fileName = "behavior_monitor_$date.csv"
        val csvContent = buildCsv(events)

        // Android 10+ 使用 MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveViaMediaStore(context, fileName, csvContent)
        } else {
            return saveViaFileApi(fileName, csvContent)
        }
    }

    private fun buildCsv(events: List<StateEvent>): String {
        val sb = StringBuilder()
        sb.appendLine("timestamp,from_state,to_state,confidence")
        events.forEach { event ->
            val timeStr = csvDateFormat.format(Date(event.timestamp))
            sb.appendLine("$timeStr,${event.fromState},${event.toState},${"%.2f".format(event.confidence)}")
        }
        return sb.toString()
    }

    private fun saveViaMediaStore(context: Context, fileName: String, content: String): String {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Failed to create MediaStore entry")

        resolver.openOutputStream(uri)?.use { os ->
            os.write(content.toByteArray(Charsets.UTF_8))
        }

        contentValues.clear()
        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        return "Downloads/$fileName"
    }

    @Suppress("DEPRECATION")
    private fun saveViaFileApi(fileName: String, content: String): String {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        FileOutputStream(file).use { fos ->
            fos.write(content.toByteArray(Charsets.UTF_8))
        }
        return file.absolutePath
    }
}
