package com.example.sasalele.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "guidance_messages")
data class GuidanceMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clientId: String,
    val workAccountId: String,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long
)
