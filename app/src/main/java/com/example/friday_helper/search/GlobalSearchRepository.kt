package com.example.friday_helper.search

import android.content.Context
import com.example.friday_helper.chat.AppDatabase
import com.example.friday_helper.chat.MessageSearchRow
import com.example.friday_helper.notes.NotesRepository
import com.example.friday_helper.security.SecuritySession
import com.example.friday_helper.tasks.TaskEntity
import com.example.friday_helper.tasks.TasksDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GlobalSearchRepository {

    private const val MAX_QUERY_LEN = 256

    suspend fun search(context: Context, rawQuery: String): List<GlobalSearchListItem> = withContext(Dispatchers.IO) {
        val trimmed = rawQuery.trim().take(MAX_QUERY_LEN)
        if (trimmed.isEmpty()) return@withContext emptyList()

        val appDb = AppDatabase.getInstance(context)
        val tasksDb = TasksDatabase.getInstance(context)

        val messages = appDb.messageDao().searchMessagesByContent(trimmed)
        val tasks = tasksDb.taskDao().searchTasks(trimmed)

        val noteHits = if (!SecuritySession.isGuest) {
            val repo = NotesRepository(context)
            repo.getNotes().mapNotNull { note ->
                val hitTitle = note.title.contains(trimmed, ignoreCase = true)
                val hitText = note.text.contains(trimmed, ignoreCase = true)
                if (!hitTitle && !hitText) return@mapNotNull null
                val preview = when {
                    note.text.isNotBlank() -> snippet(note.text)
                    note.title.isNotBlank() -> snippet(note.title)
                    else -> ""
                }
                GlobalSearchListItem.NoteHit(note.id, note.title, preview)
            }
        } else {
            emptyList()
        }

        buildList {
            if (messages.isNotEmpty()) {
                add(GlobalSearchListItem.Header(GlobalSearchListItem.Section.CHATS))
                messages.forEach { add(GlobalSearchListItem.ChatHit(it)) }
            }
            if (noteHits.isNotEmpty()) {
                add(GlobalSearchListItem.Header(GlobalSearchListItem.Section.NOTES))
                noteHits.forEach { add(it) }
            }
            if (tasks.isNotEmpty()) {
                add(GlobalSearchListItem.Header(GlobalSearchListItem.Section.TASKS))
                tasks.forEach { add(GlobalSearchListItem.TaskHit(it)) }
            }
        }
    }

    fun snippet(text: String, maxLen: Int = 160): String {
        val t = text.trim().replace("\n", " ").trim()
        if (t.length <= maxLen) return t
        return t.take(maxLen).trimEnd() + "…"
    }

    fun chatSubtitle(row: MessageSearchRow): String {
        val who = if (row.role == "user") "Вы" else "ИИ"
        return "$who · ${snippet(row.content)}"
    }

    fun taskSubtitle(task: TaskEntity): String {
        val parts = listOfNotNull(
            task.subtitle.takeIf { it.isNotBlank() },
            task.tags.takeIf { it.isNotBlank() }
        )
        return snippet(parts.joinToString(" · "))
    }
}

sealed class GlobalSearchListItem {
    enum class Section { CHATS, NOTES, TASKS }

    data class Header(val section: Section) : GlobalSearchListItem()

    data class ChatHit(val row: MessageSearchRow) : GlobalSearchListItem()

    data class NoteHit(val noteId: Long, val title: String, val preview: String) : GlobalSearchListItem()

    data class TaskHit(val task: TaskEntity) : GlobalSearchListItem()
}
