package com.behaviormonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.behaviormonitor.domain.model.MonitorState
import com.behaviormonitor.domain.model.StateEvent

/**
 * Room 实体：状态变化事件
 */
@Entity(tableName = "state_events")
data class StateEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val fromState: String,
    val toState: String,
    val confidence: Float
)

fun StateEventEntity.toDomain(): StateEvent = StateEvent(
    id = id,
    timestamp = timestamp,
    fromState = MonitorState.entries.firstOrNull { it.name == fromState } ?: MonitorState.UNKNOWN,
    toState = MonitorState.entries.firstOrNull { it.name == toState } ?: MonitorState.UNKNOWN,
    confidence = confidence
)

fun StateEvent.toEntity(): StateEventEntity = StateEventEntity(
    id = id,
    timestamp = timestamp,
    fromState = fromState.name,
    toState = toState.name,
    confidence = confidence
)
