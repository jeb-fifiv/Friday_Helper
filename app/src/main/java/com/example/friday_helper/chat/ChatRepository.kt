package com.example.friday_helper.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository(private val db: AppDatabase) {

    suspend fun getChatsList(): List<ChatEntity> = withContext(Dispatchers.IO) {
        db.chatDao().getAllChats()
    }

    suspend fun getMessages(chatId: Long): List<MessageEntity> = withContext(Dispatchers.IO) {
        db.messageDao().getMessagesByChatId(chatId)
    }

    suspend fun createChat(title: String = "Новый чат"): Long = withContext(Dispatchers.IO) {
        db.chatDao().insert(ChatEntity(title = title))
    }

    suspend fun getChat(id: Long): ChatEntity? = withContext(Dispatchers.IO) {
        db.chatDao().getChatById(id)
    }

    suspend fun updateChatTitle(chatId: Long, title: String) = withContext(Dispatchers.IO) {
        db.chatDao().getChatById(chatId)?.let { chat ->
            db.chatDao().update(chat.copy(title = title))
        }
    }

    suspend fun addMessage(chatId: Long, role: String, content: String) = withContext(Dispatchers.IO) {
        db.messageDao().insert(MessageEntity(chatId = chatId, role = role, content = content))
    }

    suspend fun setChatTitleFromFirstMessage(chatId: Long) = withContext(Dispatchers.IO) {
        val first = db.messageDao().getFirstMessage(chatId)
        if (first != null && first.role == "user") {
            val title = first.content.take(50).let { if (it.length == 50) "$it…" else it }
            db.chatDao().getChatById(chatId)?.let { db.chatDao().update(it.copy(title = title)) }
        }
    }

    suspend fun deleteChat(id: Long) = withContext(Dispatchers.IO) {
        db.messageDao().deleteByChatId(id)
        db.chatDao().deleteById(id)
    }

    suspend fun deleteMessage(id: Long) = withContext(Dispatchers.IO) {
        db.messageDao().deleteById(id)
    }
}
