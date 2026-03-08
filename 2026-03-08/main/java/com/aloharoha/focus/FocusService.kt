package com.aloharoha.focus

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class FocusService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = true
    private val allowedApps = mutableSetOf<String>()
    private val usageMap = mutableMapOf<String, Long>()
    private var lastApp: String? = null
    private var lastTime: Long = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        val savedApps = prefs.getStringSet("allowed_packages", emptySet()) ?: emptySet()

        allowedApps.clear()
        allowedApps.addAll(savedApps)
        allowedApps.add(packageName) // 내 앱 패키지명 추가

        // 홈 화면(런처)들 자동 허용
        val intentHome = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intentHome, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            packageManager.queryIntentActivities(intentHome, 0)
        }
        for (info in resolveInfos) {
            allowedApps.add(info.activityInfo.packageName)
        }
        allowedApps.add("com.android.systemui")

        startForeground(99, createNotification())
        isRunning = true
        lastTime = System.currentTimeMillis()

        serviceScope.launch {
            while (isRunning) {
                val currentPackage = getForegroundPackage()
                if (currentPackage != null) {
                    // ⭐ 내 앱(packageName)이면 절대 차단 안 함!
                    if (currentPackage != packageName && !allowedApps.contains(currentPackage)) {
                        moveToHome()
                    } else {
                        updateUsage(currentPackage)
                    }
                }
                delay(500) // 0.5초마다 빠르게 확인
            }
        }
        return START_STICKY
    }

    private fun getForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // 최근 8초간의 이벤트를 뒤져서 현재 앱을 찾음
        val events = usm.queryEvents(now - 8000, now)
        val event = UsageEvents.Event()
        var lastPkg: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPkg = event.packageName
            }
        }
        return lastPkg ?: lastApp
    }

    private fun moveToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        try { startActivity(homeIntent) } catch (e: Exception) { }
    }

    private fun updateUsage(currentPkg: String) {
        val now = System.currentTimeMillis()
        if (lastApp == currentPkg) {
            val delta = (now - lastTime) / 1000
            if (delta > 0) {
                usageMap[currentPkg] = (usageMap[currentPkg] ?: 0L) + delta
            }
        }
        lastApp = currentPkg
        lastTime = now
    }

    private suspend fun saveUsageLogsSync() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val db = AppDatabase.getDatabase(applicationContext)
        withContext(Dispatchers.IO) {
            usageMap.forEach { (pkg, duration) ->
                if (duration > 0) {
                    val appName = try {
                        val info = packageManager.getApplicationInfo(pkg, 0)
                        packageManager.getApplicationLabel(info).toString()
                    } catch (e: Exception) { pkg }

                    // ⭐ 타입 에러 방지를 위해 명시적 이름 사용
                    db.todoDao().insertUsageLog(UsageLog(
                        packageName = pkg,
                        appName = appName,
                        durationSeconds = duration,
                        date = today
                    ))
                }
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "focus_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Focus Mode", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("집중 모드 작동 중")
            .setContentText("허용되지 않은 앱 사용 시 홈으로 이동합니다.")
            .setSmallIcon(R.drawable.ic_focus_logo)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        runBlocking { saveUsageLogsSync() }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? = null
}