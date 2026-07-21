package com.behaviormonitor.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.behaviormonitor.BehaviorMonitorApplication
import com.behaviormonitor.MainActivity
import com.behaviormonitor.R
import com.behaviormonitor.data.camera.CameraManager
import com.behaviormonitor.data.local.AppDatabase
import com.behaviormonitor.data.repository.StateEventRepositoryImpl
import com.behaviormonitor.data.tflite.DetectionResult
import com.behaviormonitor.data.tflite.PersonDetector
import com.behaviormonitor.domain.model.DailySummary
import com.behaviormonitor.domain.model.MonitorState
import com.behaviormonitor.domain.model.StateEvent
import com.behaviormonitor.domain.statemachine.PresenceStateMachine
import com.behaviormonitor.domain.repository.StateEventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 前台监测服务
 * 在后台持续运行人形检测与状态监测
 */
class MonitorService : android.app.Service() {

    private lateinit var cameraManager: CameraManager
    private lateinit var personDetector: PersonDetector
    private lateinit var stateMachine: PresenceStateMachine
    private lateinit var stateEventRepository: StateEventRepository

    private var monitoringJob: Job? = null
    private var summaryJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /** 服务状态，供 ViewModel/Activity 观察 */
    private val _serviceState = MutableStateFlow(MonitorServiceState())
    val serviceState: StateFlow<MonitorServiceState> = _serviceState.asStateFlow()

    companion object {
        const val NOTIFICATION_ID = 10001
        const val ACTION_START = "com.behaviormonitor.action.START_MONITORING"
        const val ACTION_STOP = "com.behaviormonitor.action.STOP_MONITORING"

        private val _instance = MutableStateFlow<MonitorService?>(null)
        val instance: StateFlow<MonitorService?> = _instance.asStateFlow()

        private const val TAG = "MonitorService"
    }

    override fun onCreate() {
        super.onCreate()
        _instance.value = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startMonitoringInternal()
            }
        }
        // 异常退出后自动重启
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        _instance.value = null
        super.onDestroy()
    }

    private fun startMonitoringInternal() {
        // 初始化组件
        cameraManager = CameraManager(this)
        personDetector = PersonDetector(this)
        stateMachine = PresenceStateMachine()
        stateEventRepository = StateEventRepositoryImpl(AppDatabase.getInstance(this))

        val modelLoaded = personDetector.initialize()
        if (!modelLoaded) {
            Log.e(TAG, "Model failed to load, stopping service")
            stopSelf()
            return
        }

        // 启动前台通知
        val notification = buildNotification("监测中")
        startForeground(NOTIFICATION_ID, notification)

        // 获取 WakeLock 保持 CPU 运行
        acquireWakeLock()

        // 重置状态机
        stateMachine.reset()
        serviceScope.launch(Dispatchers.IO) {
            stateEventRepository.clearAll()
        }

        // 启动相机（Service 无 LifecycleOwner，使用独立方法）
        cameraManager.startCameraWithoutLifecycle(fps = 5)

        _serviceState.value = MonitorServiceState(
            isMonitoring = true,
            currentState = MonitorState.UNKNOWN
        )

        // 启动监测协程
        monitoringJob = serviceScope.launch(Dispatchers.Default) {
            cameraManager.frameFlow.collectLatest { bitmap ->
                if (bitmap == null) return@collectLatest

                // 逆时针旋转 90 度修正前置摄像头方向
                val rotated = rotateBitmap(bitmap, -90f)

                // 人形检测
                val result = personDetector.detect(rotated)

                // 更新预览位图
                val preview = drawDetections(rotated, result)
                _serviceState.value = _serviceState.value.copy(
                    previewBitmap = preview
                )

                // 状态机处理
                val isPersonDetected = result.detections.isNotEmpty()
                val maxConfidence = result.detections.maxOfOrNull { it.confidence } ?: 0f
                val stateEvent = stateMachine.processDetection(isPersonDetected, maxConfidence)

                // 更新服务状态
                _serviceState.value = _serviceState.value.copy(
                    currentState = stateMachine.currentState,
                    detectionCount = result.detections.size,
                    avgInferenceTimeMs = result.inferenceTimeMs,
                    frameCount = _serviceState.value.frameCount + 1L
                )

                // 保存状态变化事件
                if (stateEvent != null) {
                    Log.d(TAG, "${stateEvent.fromState} → ${stateEvent.toState}, conf=${stateEvent.confidence}")
                    withContext(Dispatchers.IO) {
                        stateEventRepository.saveEvent(stateEvent)
                    }
                    // 更新通知文字
                    updateNotification(stateMachine.currentState)
                }
            }
        }

        // 监听当日事件计算统计
        summaryJob = serviceScope.launch(Dispatchers.IO) {
            stateEventRepository.getEventsByDate(dateFormat.format(Date())).collectLatest { events ->
                val summary = calculateSummary(events)
                _serviceState.value = _serviceState.value.copy(dailySummary = summary)
            }
        }
    }

    private fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        summaryJob?.cancel()
        summaryJob = null
        cameraManager.stopCamera()
        cameraManager.release()
        personDetector.release()
        releaseWakeLock()
        _serviceState.value = MonitorServiceState()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BehaviorMonitor::MonitorWakeLock"
        ).apply {
            acquire(8 * 60 * 60 * 1000L) // 最长 8 小时
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun buildNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, BehaviorMonitorApplication.CHANNEL_ID_MONITORING)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(state: MonitorState) {
        val text = when (state) {
            MonitorState.PRESENT -> "监测中 - 在岗"
            MonitorState.ABSENT -> "监测中 - 离岗"
            MonitorState.UNKNOWN -> "监测中"
        }
        val notification = buildNotification(text)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun drawDetections(bitmap: Bitmap, result: DetectionResult): Bitmap {
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val paint = Paint().apply {
            color = AndroidColor.GREEN
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        val textPaint = Paint().apply {
            color = AndroidColor.GREEN
            textSize = 24f
            isAntiAlias = true
        }

        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        result.detections.forEach { detection ->
            val box = detection.boundingBox
            val left = box.xmin * width
            val top = box.ymin * height
            val right = box.xmax * width
            val bottom = box.ymax * height

            canvas.drawRect(left, top, right, bottom, paint)
            val text = String.format("%.0f%%", detection.confidence * 100)
            canvas.drawText(text, left, top - 8f, textPaint)
        }

        return outputBitmap
    }

    private fun calculateSummary(events: List<StateEvent>): DailySummary {
        if (events.isEmpty()) {
            return DailySummary(
                date = dateFormat.format(Date()),
                presentDuration = 0L,
                absentDuration = 0L,
                absentCount = 0
            )
        }

        var presentDuration = 0L
        var absentDuration = 0L
        var absentCount = 0

        for (i in events.indices) {
            val event = events[i]
            val nextTimestamp = if (i + 1 < events.size) events[i + 1].timestamp else System.currentTimeMillis()
            val duration = nextTimestamp - event.timestamp

            when (event.toState) {
                MonitorState.PRESENT -> presentDuration += duration
                MonitorState.ABSENT -> {
                    absentDuration += duration
                    absentCount++
                }
                MonitorState.UNKNOWN -> { /* 忽略 */ }
            }
        }

        return DailySummary(
            date = dateFormat.format(Date()),
            presentDuration = presentDuration,
            absentDuration = absentDuration,
            absentCount = absentCount
        )
    }
}

/**
 * 服务状态数据类
 */
data class MonitorServiceState(
    val isMonitoring: Boolean = false,
    val currentState: MonitorState = MonitorState.UNKNOWN,
    val dailySummary: DailySummary? = null,
    val detectionCount: Int = 0,
    val avgInferenceTimeMs: Long = 0L,
    val frameCount: Long = 0L,
    val previewBitmap: Bitmap? = null
)
