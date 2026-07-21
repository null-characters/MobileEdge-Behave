package com.behaviormonitor.domain.repository

import com.behaviormonitor.domain.model.StateEvent
import kotlinx.coroutines.flow.Flow

/**
 * 状态事件仓库接口
 */
interface StateEventRepository {
    /**
     * 保存状态事件
     */
    suspend fun saveEvent(event: StateEvent)

    /**
     * 获取指定日期的状态事件
     */
    fun getEventsByDate(date: String): Flow<List<StateEvent>>

    /**
     * 获取所有状态事件
     */
    fun getAllEvents(): Flow<List<StateEvent>>
}
