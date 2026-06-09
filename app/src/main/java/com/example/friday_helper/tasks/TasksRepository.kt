package com.example.friday_helper.tasks

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class TasksRepository(private val db: TasksDatabase) {

    /** Начало дня в миллисекундах (локальное время). */
    fun dayStartMillis(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    suspend fun getTasksForDay(dayStartMillis: Long): List<TaskWithSubtasks> = withContext(Dispatchers.IO) {
        val tasks = db.taskDao().getTasksByDay(dayStartMillis)
        tasks.map { task ->
            val subtasks = db.subtaskDao().getByTaskId(task.id)
            TaskWithSubtasks(task, subtasks)
        }
    }

    suspend fun getAllTasksWithSubtasks(): List<TaskWithSubtasks> = withContext(Dispatchers.IO) {
        val tasks = db.taskDao().getAllTasks()
        tasks.map { task ->
            val subtasks = db.subtaskDao().getByTaskId(task.id)
            TaskWithSubtasks(task, subtasks)
        }
    }

    suspend fun insertTask(task: TaskEntity, subtaskTitles: List<String>): Long = withContext(Dispatchers.IO) {
        val id = db.taskDao().insert(task)
        subtaskTitles.forEachIndexed { index, title ->
            db.subtaskDao().insert(SubtaskEntity(taskId = id, title = title, done = false, sortOrder = index))
        }
        id
    }

    suspend fun updateTask(task: TaskEntity, subtasks: List<SubtaskEntity>) = withContext(Dispatchers.IO) {
        db.taskDao().update(task)
        db.subtaskDao().deleteByTaskId(task.id)
        subtasks.forEachIndexed { index, sub ->
            db.subtaskDao().insert(SubtaskEntity(taskId = task.id, title = sub.title, done = sub.done, sortOrder = index))
        }
    }

    suspend fun toggleTaskDone(task: TaskEntity) = withContext(Dispatchers.IO) {
        db.taskDao().update(task.copy(done = !task.done))
    }

    suspend fun toggleSubtaskDone(subtask: SubtaskEntity) = withContext(Dispatchers.IO) {
        db.subtaskDao().update(subtask.copy(done = !subtask.done))
    }

    suspend fun deleteTask(id: Long) = withContext(Dispatchers.IO) {
        db.subtaskDao().deleteByTaskId(id)
        db.taskDao().deleteById(id)
    }

    suspend fun getTaskWithSubtasks(id: Long): TaskWithSubtasks? = withContext(Dispatchers.IO) {
        db.taskDao().getTaskById(id)?.let { task ->
            TaskWithSubtasks(task, db.subtaskDao().getByTaskId(task.id))
        }
    }

    suspend fun getTaskDayStartsInRange(startInclusive: Long, endInclusive: Long): Set<Long> = withContext(Dispatchers.IO) {
        db.taskDao().getTaskDayStartsInRange(startInclusive, endInclusive).toSet()
    }

    data class TaskWithSubtasks(val task: TaskEntity, val subtasks: List<SubtaskEntity>)
}
