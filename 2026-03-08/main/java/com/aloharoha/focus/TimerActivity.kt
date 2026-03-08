package com.aloharoha.focus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimerActivity : AppCompatActivity() {
    private lateinit var chronometer: Chronometer
    private var taskId: Long = -1
    private var startTimeStamp: Long = 0
    private var isPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timer)

        chronometer = findViewById(R.id.chronometer)
        taskId = intent.getLongExtra("task_id", -1L)
        val title = intent.getStringExtra("todo_title") ?: "집중"
        findViewById<TextView>(R.id.text_timer_title).text = "'$title' 집중 중"

        // 버튼 리스너
        findViewById<Button>(R.id.btn_stop_timer).setOnClickListener { pauseFocus() }
        findViewById<Button>(R.id.btn_resume_timer).setOnClickListener { resumeFocus() }
        findViewById<Button>(R.id.btn_exit_timer).setOnClickListener {
            val isChecked = findViewById<CheckBox>(R.id.checkbox_complete_task).isChecked
            saveAndExit(isChecked)
        }

        loadAndStart()
    }

    private fun loadAndStart() {
        val prefs = getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
        val savedId = prefs.getLong("active_task_id", -1L)

        // 새로 만든 할 일이나 다른 할 일이면 초기화
        if (savedId != taskId) {
            prefs.edit().clear().apply()
            startTimeStamp = SystemClock.elapsedRealtime()
        } else {
            startTimeStamp = prefs.getLong("start_timestamp", SystemClock.elapsedRealtime())
        }

        chronometer.base = startTimeStamp
        chronometer.start()
        saveState(true)
        startService(Intent(this, FocusService::class.java))
    }

    private fun saveState(running: Boolean) {
        getSharedPreferences("timer_prefs", Context.MODE_PRIVATE).edit().apply {
            putLong("active_task_id", taskId)
            putLong("start_timestamp", startTimeStamp)
            putBoolean("is_running", running)
            apply()
        }
    }

    private fun pauseFocus() {
        isPaused = true
        chronometer.stop()
        val elapsed = SystemClock.elapsedRealtime() - chronometer.base

        getSharedPreferences("timer_prefs", Context.MODE_PRIVATE).edit().apply {
            putBoolean("is_running", false)
            putLong("paused_offset", elapsed)
            apply()
        }

        stopService(Intent(this, FocusService::class.java))

        // UI 전환: 타이머 숨기고 결과(다시시작/종료) 화면 보여주기
        findViewById<View>(R.id.layout_timer).visibility = View.GONE
        findViewById<View>(R.id.layout_result).visibility = View.VISIBLE
        findViewById<TextView>(R.id.text_result_time).text = "집중 시간: ${elapsed / 60000}분 ${(elapsed % 60000) / 1000}초"
    }

    private fun resumeFocus() {
        isPaused = false
        val prefs = getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
        val offset = prefs.getLong("paused_offset", 0L)

        // 시간 이어서 시작하게 베이스 재설정
        startTimeStamp = SystemClock.elapsedRealtime() - offset
        chronometer.base = startTimeStamp

        saveState(true)
        chronometer.start()
        startService(Intent(this, FocusService::class.java))

        // UI 전환: 다시 타이머 화면으로
        findViewById<View>(R.id.layout_result).visibility = View.GONE
        findViewById<View>(R.id.layout_timer).visibility = View.VISIBLE
    }

    private fun saveAndExit(completed: Boolean) {
        val prefs = getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
        val elapsedMillis = if (isPaused) {
            prefs.getLong("paused_offset", 0L)
        } else {
            SystemClock.elapsedRealtime() - chronometer.base
        }
        val currentSessionSeconds = elapsedMillis / 1000

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@TimerActivity)
            withContext(Dispatchers.IO) {
                val task = db.todoDao().getTodoById(taskId)

                // ⭐ 바로 이 부분이야!
                task?.let {
                    db.todoDao().update(it.copy(
                        isCompleted = completed,
                        focusTime = it.focusTime + currentSessionSeconds
                    ))
                }
            }
            // ... (나머지 코드: prefs 삭제, stopService 등)
            prefs.edit().clear().apply()
            stopService(Intent(this@TimerActivity, FocusService::class.java))
            setResult(RESULT_OK)
            finish()
        }
    }
}