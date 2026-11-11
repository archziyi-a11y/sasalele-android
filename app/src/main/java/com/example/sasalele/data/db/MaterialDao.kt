package com.example.sasalele.data.db

import androidx.room.Delete
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {
    @Query("SELECT * FROM materials WHERE id=:id")
    suspend fun byId(id:Int):MaterialEntity?
    @Query("SELECT * FROM material_naming_outbox WHERE source LIKE :prefix || '%' AND state='pending' ORDER BY id")
    suspend fun pendingNaming(prefix:String):List<MaterialNamingEvent>

    @Insert
    suspend fun addNaming(event:MaterialNamingEvent)

    @Query("DELETE FROM material_naming_outbox WHERE id=:id")
    suspend fun removeNaming(id:Int)

    @Query("UPDATE material_naming_outbox SET state='rejected' WHERE id=:id")
    suspend fun rejectNaming(id:Int)

    @Query("SELECT COUNT(*) FROM material_naming_outbox WHERE source LIKE :prefix || '%' AND state='rejected'")
    suspend fun rejectedNaming(prefix:String):Int

    @Query("DELETE FROM material_naming_outbox WHERE state='rejected' AND id NOT IN (SELECT id FROM material_naming_outbox WHERE state='rejected' ORDER BY id DESC LIMIT 100)")
    suspend fun trimRejected()

    @androidx.room.Transaction
    suspend fun saveNamed(material:MaterialEntity,event:MaterialNamingEvent?) {
        saveTeam(material)
        if(event!=null)addNaming(event)
    }
    @Query("SELECT * FROM materials WHERE teamSourceId = :source LIMIT 1")
    suspend fun byTeamSource(source: String): MaterialEntity?

    @Query("SELECT * FROM materials WHERE teamSourceId IS NULL ORDER BY id DESC")
    suspend fun unlinked(): List<MaterialEntity>

    @androidx.room.Transaction
    suspend fun saveTeam(material: MaterialEntity) {
        val old = byTeamSource(requireNotNull(material.teamSourceId))
        if (old == null && material.id>0) {
            val previous=byId(material.id)
            if(previous==null)insert(material.copy(id=0))
            else {require(previous.teamSourceId==null || previous.teamSourceId==material.teamSourceId);update(material)}
        }
        else if (old == null) insert(material.copy(id=0))
        else update(material.copy(id=old.id,isFavorite=old.isFavorite))
    }
    @Query("SELECT * FROM materials ORDER BY isFavorite DESC, id DESC")
    fun getAllMaterials(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE title LIKE '%' || :query || '%' ORDER BY isFavorite DESC, id DESC")
    fun searchMaterials(query: String): Flow<List<MaterialEntity>>

    @Insert
    suspend fun insert(material: MaterialEntity)

    @Insert
    suspend fun insertAll(materials: List<MaterialEntity>)

    @Query("SELECT COUNT(*) FROM materials WHERE content = :content")
    suspend fun countContent(content: String): Int

    @Delete
    suspend fun delete(material: MaterialEntity)

    @Update
    suspend fun update(material: MaterialEntity)
}
