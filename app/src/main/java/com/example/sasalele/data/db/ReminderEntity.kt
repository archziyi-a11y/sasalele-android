package com.example.sasalele.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val workAccountId: String,
    val clientId: String,
    val retryCount: Int,
    val nextScheduleTime: Long,
    val status: String,
    val messagesJson: String,
    @androidx.room.ColumnInfo(defaultValue = "0") val lastDailyReminderTime: Long = 0L,
    @androidx.room.ColumnInfo(defaultValue = "0") val closedAt: Long = 0L
)
