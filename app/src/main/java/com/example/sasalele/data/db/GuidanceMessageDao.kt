package com.example.sasalele.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GuidanceMessageDao {
    @Query("SELECT * FROM guidance_messages WHERE clientId = :clientId AND workAccountId = :workAccountId ORDER BY timestamp ASC")
    fun getMessagesForClient(clientId: String, workAccountId: String): Flow<List<GuidanceMessageEntity>>

    @Insert
    suspend fun insertMessage(message: GuidanceMessageEntity)
}
