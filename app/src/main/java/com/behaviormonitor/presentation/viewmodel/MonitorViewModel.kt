package com.behaviormonitor.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behaviormonitor.data.export.CsvExporter
import com.behaviormonitor.data.local.AppDatabase
import com.behaviormonitor.data.repository.StateEventRepositoryImpl
import com.behaviormonitor.domain.model.DailySummary
import com.behaviormonitor.domain.model.MonitorState
import com.behaviormonitor.service.MonitorService
import com.behaviormonitor.service.MonitorServiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 监测 ViewModel
 * 通过 Foreground Service 控制监测生命周期
 */
class MonitorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private var serviceObserverJob: kotlinx.coroutines.Job? = null
    private var stateCollectJob: kotlinx.coroutines.Job? = null

    /**
     * 开始监测 - 启动 Foreground Service
     */
    fun startMonitoring(context: Context) {
        val intent = Intent(context, MonitorService::class.java).apply {
            action = MonitorService.ACTION_START
        }
        context.startForegroundService(intent)

        _uiState.value = UiState(isMonitoring = true, currentState = MonitorState.UNKNOWN)

        // 观察 Service 状态
        observeServiceState()
    }

    /**
     * 停止监测 - 停止 Foreground Service
     */
    fun stopMonitoring(context: Context) {
        val intent = Intent(context, MonitorService::class.java).apply {
            action = MonitorService.ACTION_STOP
        }
        context.startService(intent)

        serviceObserverJob?.cancel()
        serviceObserverJob = null
        stateCollectJob?.cancel()
        stateCollectJob = null
        _previewBitmap.value = null
        _uiState.value = UiState(isMonitoring = false)
    }

    /**
     * 观察 Service 状态变化
     */
    private fun observeServiceState() {
        serviceObserverJob?.cancel()
        stateCollectJob?.cancel()
        serviceObserverJob = viewModelScope.launch(Dispatchers.Main) {
            MonitorService.instance.collectLatest { service ->
                // 取消上一个 Service 的状态收集
                stateCollectJob?.cancel()
                if (service == null) {
                    _uiState.value = UiState(isMonitoring = false)
                    _previewBitmap.value = null
                } else {
                    // 收集 Service 内部状态
                    stateCollectJob = viewModelScope.launch(Dispatchers.Main) {
                        service.serviceState.collect { state ->
                            _uiState.value = UiState(
                                isMonitoring = state.isMonitoring,
                                currentState = state.currentState,
                                dailySummary = state.dailySummary,
                                detectionCount = state.detectionCount,
                                avgInferenceTimeMs = state.avgInferenceTimeMs,
                                frameCount = state.frameCount
                            )
                            _previewBitmap.value = state.previewBitmap
                        }
                    }
                }
            }
        }
    }

    /**
     * 检查 Service 是否正在运行
     */
    fun checkServiceState() {
        observeServiceState()
    }

    /**
     * 导出当日 CSV
     */
    fun exportCsv(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repo = StateEventRepositoryImpl(AppDatabase.getInstance(context))
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
                val events = repo.getEventsByDateOnce(date)
                if (events.isEmpty()) {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "当日无数据可导出", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val path = CsvExporter.export(context, events, date)
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "已导出到 $path", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        serviceObserverJob?.cancel()
        stateCollectJob?.cancel()
    }
}
