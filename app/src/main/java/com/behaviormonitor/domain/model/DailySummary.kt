package com.behaviormonitor.domain.model

/**
 * 日统计数据
 */
data class DailySummary(
    val date: String,
    val presentDuration: Long,      // 在岗时长（毫秒）
    val absentDuration: Long,       // 离岗时长（毫秒）
    val absentCount: Int            // 离岗次数
)
