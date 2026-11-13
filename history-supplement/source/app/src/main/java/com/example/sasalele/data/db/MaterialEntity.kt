package com.example.sasalele.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "materials")
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val type: String, // "TEXT" or "IMAGE"
    val isFavorite: Boolean = false
)
