package com.behaviormonitor.data.local

import androidx.room.TypeConverter
import com.behaviormonitor.domain.model.MonitorState

/**
 * Room 类型转换器：MonitorState ↔ String
 */
class MonitorStateConverter {

    @TypeConverter
    fun fromMonitorState(state: MonitorState): String = state.name

    @TypeConverter
    fun toMonitorState(value: String): MonitorState =
        MonitorState.entries.firstOrNull { it.name == value } ?: MonitorState.UNKNOWN
}
