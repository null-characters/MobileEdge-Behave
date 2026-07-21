package com.behaviormonitor.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.behaviormonitor.domain.model.MonitorState
import com.behaviormonitor.domain.model.StateEvent
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV 导出工具
 * 格式：start_time,end_time,state,confidence
 * 将离散事件转换为连续时间段，未监测时段标记为 UNKNOWN
 */
object CsvExporter {

    private val csvDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * 导出事件列表为 CSV 文件
     * @param events 当日所有事件（按时间升序）
     * @param date 日期字符串 yyyy-MM-dd
     * @return 导出文件路径
     */
    fun export(context: Context, events: List<StateEvent>, date: String): String {
        val fileName = "behavior_monitor_$date.csv"
        val csvContent = buildCsv(events, date)

        // Android 10+ 使用 MediaStore
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, fileName, csvContent)
        } else {
            saveViaFileApi(fileName, csvContent)
        }
    }

    /**
     * 将离散事件转换为连续时间段
     * 
     * 事件序列中：
     * - fromState=UNKNOWN, toState=UNKNOWN → 监测开始标记
     * - fromState=X, toState=UNKNOWN → 监测停止标记
     * - 其他 → 正常状态切换
     * 
     * 输出格式：每个时间段一行，start_time,end_time,state
     */
    private fun buildCsv(events: List<StateEvent>, date: String): String {
        val sb = StringBuilder()
        sb.appendLine("start_time,end_time,state,confidence")

        if (events.isEmpty()) return sb.toString()

        // 当天 00:00 和当前时间作为边界
        val dayStart = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)?.time ?: 0L
        val now = System.currentTimeMillis()

        // 解析事件序列，构建时间段
        var currentTime = dayStart
        var currentState = MonitorState.UNKNOWN
        var currentConfidence = 0f
        var isMonitoring = false

        for (event in events) {
            val isStartMarker = event.fromState == MonitorState.UNKNOWN && event.toState == MonitorState.UNKNOWN && event.confidence == 0f
            val isStopMarker = event.toState == MonitorState.UNKNOWN && event.confidence == 0f && !isStartMarker

            if (isStartMarker) {
                // 监测开始：先输出之前的 UNKNOWN 时段
                if (currentTime < event.timestamp) {
                    sb.appendLine(formatPeriod(currentTime, event.timestamp, currentState, currentConfidence))
                }
                currentTime = event.timestamp
                isMonitoring = true
                currentState = MonitorState.UNKNOWN
                currentConfidence = 0f
            } else if (isStopMarker) {
                // 监测停止：输出当前状态时段
                if (currentTime < event.timestamp) {
                    sb.appendLine(formatPeriod(currentTime, event.timestamp, currentState, currentConfidence))
                }
                currentTime = event.timestamp
                isMonitoring = false
                currentState = MonitorState.UNKNOWN
                currentConfidence = 0f
            } else {
                // 正常状态切换：输出上一状态时段
                if (currentTime < event.timestamp) {
                    sb.appendLine(formatPeriod(currentTime, event.timestamp, currentState, currentConfidence))
                }
                currentTime = event.timestamp
                currentState = event.toState
                currentConfidence = event.confidence
            }
        }

        // 最后一个事件到当前时间的时段
        if (currentTime < now) {
            sb.appendLine(formatPeriod(currentTime, now, currentState, currentConfidence))
        }

        return sb.toString()
    }

    private fun formatPeriod(start: Long, end: Long, state: MonitorState, confidence: Float): String {
        val startStr = csvDateFormat.format(Date(start))
        val endStr = csvDateFormat.format(Date(end))
        val stateStr = if (state == MonitorState.UNKNOWN) "UNKNOWN" else state.name
        return "$startStr,$endStr,$stateStr,${"%.2f".format(confidence)}"
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
