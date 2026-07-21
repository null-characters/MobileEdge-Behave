package com.behaviormonitor.data.repository

import com.behaviormonitor.data.local.AppDatabase
import com.behaviormonitor.data.local.toDomain
import com.behaviormonitor.data.local.toEntity
import com.behaviormonitor.domain.model.StateEvent
import com.behaviormonitor.domain.repository.StateEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 状态事件仓库实现
 */
class StateEventRepositoryImpl(
    private val database: AppDatabase
) : StateEventRepository {

    private val dao = database.stateEventDao()

    override suspend fun saveEvent(event: StateEvent) {
        dao.insert(event.toEntity())
    }

    override fun getEventsByDate(date: String): Flow<List<StateEvent>> {
        return dao.queryByDate(date).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllEvents(): Flow<List<StateEvent>> {
        return dao.queryAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun clearAll() {
        dao.deleteAll()
    }
}
