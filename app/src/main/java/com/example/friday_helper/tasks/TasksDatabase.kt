package com.example.friday_helper.tasks

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, SubtaskEntity::class],
    version = 2,
    exportSchema = false
)
abstract class TasksDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun subtaskDao(): SubtaskDao

    companion object {
        @Volatile
        private var INSTANCE: TasksDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN dueTimeMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN reminderType TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN reminderParam INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): TasksDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TasksDatabase::class.java,
                    "tasks_db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
        }
    }
}
