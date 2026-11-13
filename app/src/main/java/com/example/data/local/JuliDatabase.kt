package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.Note
import com.example.data.model.TaskItem
import com.example.data.model.UserMemory

@Database(
    entities = [Note::class, ChatMessage::class, TaskItem::class, UserMemory::class],
    version = 2,
    exportSchema = false
)
abstract class JuliDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun chatDao(): ChatDao
    abstract fun taskDao(): TaskDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: JuliDatabase? = null

        fun getDatabase(context: Context): JuliDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JuliDatabase::class.java,
                    "juli_ai_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
