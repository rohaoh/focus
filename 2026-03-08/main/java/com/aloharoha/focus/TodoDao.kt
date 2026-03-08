package com.aloharoha.focus

import androidx.room.*

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_table WHERE date = :date ORDER BY time ASC")
    suspend fun getTodosByDate(date: String): List<Todo>

    @Query("SELECT * FROM todo_table")
    suspend fun getAllTodos(): List<Todo>

    @Query("SELECT * FROM todo_table WHERE repeatDays != ''")
    suspend fun getRepeatingBaseTodos(): List<Todo>

    @Query("SELECT COUNT(*) FROM todo_table WHERE repeatId = :repeatId AND date = :date")
    suspend fun checkExists(repeatId: Long, date: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: Todo)

    @Update
    suspend fun update(todo: Todo)

    @Delete
    suspend fun delete(todo: Todo)

    @Insert
    suspend fun insertUsageLog(usageLog: UsageLog)

    @Query("SELECT * FROM usage_log_table WHERE date = :date")
    suspend fun getUsageLogsByDate(date: String): List<UsageLog>
    @Query("SELECT * FROM todo_table WHERE id = :id")
    suspend fun getTodoById(id: Long): Todo?
}