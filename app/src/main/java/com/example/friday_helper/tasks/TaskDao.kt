package com.example.friday_helper.tasks

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE dueDateMillis = :dayStartMillis ORDER BY sortOrder, createdAt")
    fun getTasksByDay(dayStartMillis: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks ORDER BY dueDateMillis, sortOrder, createdAt")
    fun getAllTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun getTaskById(id: Long): TaskEntity?

    @Query("SELECT DISTINCT dueDateMillis FROM tasks WHERE dueDateMillis BETWEEN :startInclusive AND :endInclusive")
    fun getTaskDayStartsInRange(startInclusive: Long, endInclusive: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(task: TaskEntity): Long

    @Update
    fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    fun deleteById(id: Long)

    @Query(
        """
        SELECT DISTINCT t.* FROM tasks t
        LEFT JOIN subtasks s ON s.taskId = t.id
        WHERE t.title LIKE '%' || :query || '%'
           OR t.subtitle LIKE '%' || :query || '%'
           OR t.tags LIKE '%' || :query || '%'
           OR (s.title IS NOT NULL AND s.title LIKE '%' || :query || '%')
        ORDER BY t.dueDateMillis DESC, t.createdAt DESC
        LIMIT 80
        """
    )
    fun searchTasks(query: String): List<TaskEntity>
}
