package com.behaviormonitor.presentation.viewmodel

import com.behaviormonitor.domain.model.DailySummary
import com.behaviormonitor.domain.model.MonitorState

/**
 * UI 状态
 */
data class UiState(
    val currentState: MonitorState = MonitorState.UNKNOWN,
    val dailySummary: DailySummary? = null,
    val isMonitoring: Boolean = false,
    val errorMessage: String? = null,
    val detectionCount: Int = 0,
    val avgInferenceTimeMs: Long = 0L,
    val frameCount: Long = 0L
)
