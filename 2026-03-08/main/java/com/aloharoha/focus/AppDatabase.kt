package com.aloharoha.focus

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Todo::class, UsageLog::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focus_database"
                )
                    .fallbackToDestructiveMigration() // ⭐ 이 줄을 추가하세요! (버전 충돌 시 기존 데이터 밀고 새로 생성)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}