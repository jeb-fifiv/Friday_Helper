package com.example.friday_helper.chat

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    var title: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
