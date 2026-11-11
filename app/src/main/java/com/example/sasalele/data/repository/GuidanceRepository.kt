package com.example.sasalele.data.repository

import com.example.sasalele.data.db.GuidanceMessageDao
import com.example.sasalele.data.db.GuidanceMessageEntity
import kotlinx.coroutines.flow.Flow

class GuidanceRepository(private val dao: GuidanceMessageDao) {
    fun getMessagesForClient(clientId: String, workAccountId: String): Flow<List<GuidanceMessageEntity>> {
        return dao.getMessagesForClient(clientId, workAccountId)
    }

    suspend fun insertMessage(message: GuidanceMessageEntity) {
        dao.insertMessage(message)
    }
}
