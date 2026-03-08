package com.aloharoha.focus

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_log_table")
data class UsageLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val durationSeconds: Long,
    val date: String
)