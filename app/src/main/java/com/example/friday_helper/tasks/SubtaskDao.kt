package com.example.friday_helper.tasks

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SubtaskDao {

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sortOrder")
    fun getByTaskId(taskId: Long): List<SubtaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(subtask: SubtaskEntity): Long

    @Update
    fun update(subtask: SubtaskEntity)

    @Query("DELETE FROM subtasks WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    fun deleteByTaskId(taskId: Long)
}
