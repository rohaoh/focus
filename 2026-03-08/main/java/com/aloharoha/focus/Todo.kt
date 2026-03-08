package com.aloharoha.focus

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_table")
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: String,
    val time: String,
    val isCompleted: Boolean = false,
    val repeatId: Long = 0L,
    val repeatDays: String = "",
    val useTimer: Boolean = false,
    val focusTime: Long = 0L
)