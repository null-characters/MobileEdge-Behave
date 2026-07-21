package com.behaviormonitor.domain.model

/**
 * 监测状态枚举
 */
enum class MonitorState {
    UNKNOWN,  // 初始状态
    PRESENT,  // 在岗
    ABSENT    // 离岗
}
