package com.example.friday_helper.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    suspend fun getMessagesByChatId(chatId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC LIMIT 1")
    suspend fun getFirstMessage(chatId: Long): MessageEntity?

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteByChatId(chatId: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        SELECT m.id AS id, m.chatId AS chatId, m.role AS role, m.content AS content,
               m.createdAt AS createdAt, IFNULL(c.title, '') AS chatTitle
        FROM messages m
        INNER JOIN chats c ON c.id = m.chatId
        WHERE m.content LIKE '%' || :query || '%'
        ORDER BY m.createdAt DESC
        LIMIT 80
        """
    )
    suspend fun searchMessagesByContent(query: String): List<MessageSearchRow>
}

/** Строка результата поиска по телу сообщения (с названием чата). */
data class MessageSearchRow(
    val id: Long,
    val chatId: Long,
    val role: String,
    val content: String,
    val createdAt: Long,
    val chatTitle: String
)
