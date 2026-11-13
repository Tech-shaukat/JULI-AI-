package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_memory")
data class UserMemory(
    @PrimaryKey
    val key: String,
    val value: String,
    val category: String = "preference", // "preference", "fact", "custom"
    val updatedAt: Long = System.currentTimeMillis()
)
