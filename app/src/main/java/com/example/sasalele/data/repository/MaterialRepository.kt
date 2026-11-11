package com.example.sasalele.data.repository

import com.example.sasalele.data.db.MaterialDao
import com.example.sasalele.data.db.MaterialEntity
import kotlinx.coroutines.flow.Flow

class MaterialRepository(private val materialDao: MaterialDao) {
    suspend fun byId(id:Int)=materialDao.byId(id)
    suspend fun saveNamed(material:MaterialEntity,event:com.example.sasalele.data.db.MaterialNamingEvent?)=materialDao.saveNamed(material,event)
    suspend fun pendingNaming(prefix:String)=materialDao.pendingNaming(prefix)
    suspend fun rejectedNaming(prefix:String)=materialDao.rejectedNaming(prefix)
    suspend fun addNaming(event:com.example.sasalele.data.db.MaterialNamingEvent)=materialDao.addNaming(event)
    suspend fun removeNaming(id:Int)=materialDao.removeNaming(id)
    suspend fun rejectNaming(id:Int){materialDao.rejectNaming(id);materialDao.trimRejected()}
    suspend fun byTeamSource(source:String)=materialDao.byTeamSource(source)
    suspend fun unlinked()=materialDao.unlinked()
    suspend fun saveTeam(material:MaterialEntity)=materialDao.saveTeam(material)
    val allMaterials: Flow<List<MaterialEntity>> = materialDao.getAllMaterials()

    fun searchMaterials(query: String): Flow<List<MaterialEntity>> = materialDao.searchMaterials(query)

    suspend fun insert(material: MaterialEntity) = materialDao.insert(material)
    
    suspend fun insertAll(materials: List<MaterialEntity>) = materialDao.insertAll(materials)

    suspend fun delete(material: MaterialEntity, ownedDirectory: java.io.File? = null) {
        materialDao.delete(material)
        if(ownedDirectory!=null && material.content.startsWith("/") && materialDao.countContent(material.content)==0) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val file=java.io.File(material.content).canonicalFile
                if(file.parentFile==ownedDirectory.canonicalFile) file.delete()
            }
        }
    }

    suspend fun update(material: MaterialEntity) = materialDao.update(material)
}
