package com.example.sasalele.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {
    @Query("SELECT * FROM materials ORDER BY isFavorite DESC, id DESC")
    fun getAllMaterials(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE title LIKE '%' || :query || '%' ORDER BY isFavorite DESC, id DESC")
    fun searchMaterials(query: String): Flow<List<MaterialEntity>>

    @Insert
    suspend fun insert(material: MaterialEntity)

    @Insert
    suspend fun insertAll(materials: List<MaterialEntity>)

    @Update
    suspend fun update(material: MaterialEntity)
}
