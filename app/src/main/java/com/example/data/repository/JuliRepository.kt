package com.example.data.repository

import com.example.data.local.ChatDao
import com.example.data.local.MemoryDao
import com.example.data.local.NoteDao
import com.example.data.local.TaskDao
import com.example.data.model.ChatMessage
import com.example.data.model.Note
import com.example.data.model.TaskItem
import com.example.data.model.UserMemory
import kotlinx.coroutines.flow.Flow

class JuliRepository(
    private val noteDao: NoteDao,
    private val chatDao: ChatDao,
    private val taskDao: TaskDao,
    private val memoryDao: MemoryDao
) {
    // Notes
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()

    suspend fun saveNote(title: String, content: String): Long {
        val note = Note(
            title = title.trim().ifEmpty { "Note" },
            content = content.trim()
        )
        return noteDao.insertNote(note)
    }

    suspend fun deleteNote(note: Note) {
        noteDao.deleteNote(note)
    }

    suspend fun deleteNoteById(id: Long) {
        noteDao.deleteNoteById(id)
    }

    suspend fun getNotesCount(): Int {
        return noteDao.getNotesCount()
    }

    // Tasks / To-Dos
    val allTasks: Flow<List<TaskItem>> = taskDao.getAllTasks()

    suspend fun saveTask(title: String, priority: String = "Normal", dueDate: Long? = null): Long {
        val task = TaskItem(
            title = title.trim(),
            priority = priority,
            dueDate = dueDate
        )
        return taskDao.insertTask(task)
    }

    suspend fun toggleTaskCompleted(task: TaskItem) {
        taskDao.setTaskCompleted(task.id, !task.isCompleted)
    }

    suspend fun deleteTask(task: TaskItem) {
        taskDao.deleteTask(task)
    }

    // Memory & Preferences
    val allMemory: Flow<List<UserMemory>> = memoryDao.getAllMemory()

    suspend fun saveMemory(key: String, value: String, category: String = "preference") {
        memoryDao.saveMemory(UserMemory(key = key.trim(), value = value.trim(), category = category))
    }

    suspend fun getMemory(key: String): String? {
        return memoryDao.getMemoryByKey(key)?.value
    }

    suspend fun deleteMemory(key: String) {
        memoryDao.deleteMemoryByKey(key)
    }

    suspend fun clearAllMemory() {
        memoryDao.clearAllMemory()
    }

    // Chat History
    val allMessages: Flow<List<ChatMessage>> = chatDao.getAllMessages()

    suspend fun addMessage(
        text: String,
        isUser: Boolean,
        actionBadge: String? = null,
        language: String = "hi"
    ): Long {
        val message = ChatMessage(
            text = text,
            isUser = isUser,
            actionBadge = actionBadge,
            language = language
        )
        return chatDao.insertMessage(message)
    }

    suspend fun clearChatHistory() {
        chatDao.clearAllMessages()
    }
}
