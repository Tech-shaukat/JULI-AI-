package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.UserMemory
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM user_memory ORDER BY updatedAt DESC")
    fun getAllMemory(): Flow<List<UserMemory>>

    @Query("SELECT * FROM user_memory WHERE `key` = :key LIMIT 1")
    suspend fun getMemoryByKey(key: String): UserMemory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMemory(memory: UserMemory)

    @Delete
    suspend fun deleteMemory(memory: UserMemory)

    @Query("DELETE FROM user_memory WHERE `key` = :key")
    suspend fun deleteMemoryByKey(key: String)

    @Query("DELETE FROM user_memory")
    suspend fun clearAllMemory()
}
