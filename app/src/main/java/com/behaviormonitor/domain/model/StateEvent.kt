package com.behaviormonitor.domain.model

/**
 * 状态变化事件
 */
data class StateEvent(
    val id: Long = 0,
    val timestamp: Long,
    val fromState: MonitorState,
    val toState: MonitorState,
    val confidence: Float
)
