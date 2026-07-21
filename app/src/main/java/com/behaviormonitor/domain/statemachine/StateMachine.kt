package com.behaviormonitor.domain.statemachine

import com.behaviormonitor.domain.model.MonitorState
import com.behaviormonitor.domain.model.StateEvent

/**
 * 状态机接口
 */
interface StateMachine {
    /**
     * 当前状态
     */
    val currentState: MonitorState

    /**
     * 处理检测结果
     * @param isPersonDetected 是否检测到人
     * @param confidence 检测置信度
     * @return 状态变化事件，如果状态未变化则返回 null
     */
    fun processDetection(isPersonDetected: Boolean, confidence: Float): StateEvent?

    /**
     * 重置状态机
     */
    fun reset()
}
