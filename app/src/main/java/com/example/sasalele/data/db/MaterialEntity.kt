package com.example.sasalele.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "materials", indices = [Index(value = ["teamSourceId"], unique = true)])
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val type: String, // IMAGE, TEXT, LINK; standalone legacy TEXT URLs are classified as LINK at display time.
    val isFavorite: Boolean = false,
    val teamSourceId: String? = null,
    val teamAliasId: String? = null,
    val titleOrigin: String? = null
)
