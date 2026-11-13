package com.example.sasalele.data.repository

import com.example.sasalele.data.db.MaterialDao
import com.example.sasalele.data.db.MaterialEntity
import kotlinx.coroutines.flow.Flow

class MaterialRepository(private val materialDao: MaterialDao) {
    val allMaterials: Flow<List<MaterialEntity>> = materialDao.getAllMaterials()

    fun searchMaterials(query: String): Flow<List<MaterialEntity>> = materialDao.searchMaterials(query)

    suspend fun insert(material: MaterialEntity) = materialDao.insert(material)
    
    suspend fun insertAll(materials: List<MaterialEntity>) = materialDao.insertAll(materials)

    suspend fun update(material: MaterialEntity) = materialDao.update(material)
}
