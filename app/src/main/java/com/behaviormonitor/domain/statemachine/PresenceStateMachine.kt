package com.behaviormonitor.domain.statemachine

import com.behaviormonitor.domain.model.MonitorState
import com.behaviormonitor.domain.model.StateEvent

/**
 * 在岗/离岗状态机
 * 连续 CONSECUTIVE_THRESHOLD 帧检测到人 → PRESENT
 * 连续 CONSECUTIVE_THRESHOLD 帧未检测到人 → ABSENT
 */
class PresenceStateMachine(
    private val consecutiveThreshold: Int = DEFAULT_THRESHOLD
) : StateMachine {

    override var currentState: MonitorState = MonitorState.UNKNOWN
        private set

    private var consecutivePresentCount = 0
    private var consecutiveAbsentCount = 0

    override fun processDetection(isPersonDetected: Boolean, confidence: Float): StateEvent? {
        if (isPersonDetected) {
            consecutivePresentCount++
            consecutiveAbsentCount = 0
        } else {
            consecutiveAbsentCount++
            consecutivePresentCount = 0
        }

        val nextState = when {
            consecutivePresentCount >= consecutiveThreshold -> MonitorState.PRESENT
            consecutiveAbsentCount >= consecutiveThreshold -> MonitorState.ABSENT
            else -> currentState
        }

        return if (nextState != currentState) {
            val event = StateEvent(
                timestamp = System.currentTimeMillis(),
                fromState = currentState,
                toState = nextState,
                confidence = confidence
            )
            currentState = nextState
            event
        } else {
            null
        }
    }

    override fun reset() {
        currentState = MonitorState.UNKNOWN
        consecutivePresentCount = 0
        consecutiveAbsentCount = 0
    }

    companion object {
        const val DEFAULT_THRESHOLD = 5
    }
}
