package com.example.friday_helper.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ChatDao {
    @Insert
    suspend fun insert(chat: ChatEntity): Long

    @Update
    suspend fun update(chat: ChatEntity)

    @Query("SELECT * FROM chats ORDER BY createdAt DESC")
    suspend fun getAllChats(): List<ChatEntity>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getChatById(id: Long): ChatEntity?

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun deleteById(id: Long)
}
