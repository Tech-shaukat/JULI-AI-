package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val priority: String = "Normal", // "High", "Normal", "Low"
    val dueDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
