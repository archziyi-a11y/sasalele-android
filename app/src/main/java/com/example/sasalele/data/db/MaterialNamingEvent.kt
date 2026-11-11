package com.example.sasalele.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName="material_naming_outbox")
data class MaterialNamingEvent(
 @PrimaryKey(autoGenerate=true) val id:Int=0,
 val source:String,
 val path:String,
 val body:String,
 val state:String="pending"
)
