package com.example.friday_helper.tasks

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Типы напоминаний: NONE, ONCE, DAILY, EVERY_2_DAYS, EVERY_3_DAYS, EVERY_WEEK, EVERY_N_DAYS, EVERY_N_HOURS */
const val REMINDER_NONE = "NONE"
const val REMINDER_ONCE = "ONCE"
const val REMINDER_DAILY = "DAILY"
const val REMINDER_EVERY_2_DAYS = "EVERY_2_DAYS"
const val REMINDER_EVERY_3_DAYS = "EVERY_3_DAYS"
const val REMINDER_EVERY_WEEK = "EVERY_WEEK"
const val REMINDER_EVERY_N_DAYS = "EVERY_N_DAYS"
const val REMINDER_EVERY_N_HOURS = "EVERY_N_HOURS"

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subtitle: String,
    /** Начало дня в миллисекундах (локальное время). 0 = без даты. */
    val dueDateMillis: Long,
    /** Время в минутах от полуночи (0–1439). 0 = без времени. */
    val dueTimeMinutes: Int = 0,
    /** Теги через запятую: "Работа,Важно" */
    val tags: String,
    val done: Boolean,
    val cardColorIndex: Int,
    val sortOrder: Int,
    val createdAt: Long = System.currentTimeMillis(),
    /** Тип напоминания: NONE, ONCE, DAILY, EVERY_2_DAYS и т.д. */
    val reminderType: String = REMINDER_NONE,
    /** Для ONCE: момент напоминания в millis. Для EVERY_N_*: число N. */
    val reminderParam: Long = 0
)
