package com.example.friday_helper.notes

data class Folder(
    val id: Long,
    val name: String
)

data class Note(
    val id: Long,
    val title: String,
    val text: String,
    val folderId: Long? = null
)

