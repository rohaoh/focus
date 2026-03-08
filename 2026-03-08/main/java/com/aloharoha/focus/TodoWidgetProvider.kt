package com.aloharoha.focus

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TodoWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_CHECK = "com.aloharoha.focus.ACTION_CHECK"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_todo_list)

            // 1. 리스트 서비스 연결
            val serviceIntent = Intent(context, TodoWidgetService::class.java)
            views.setRemoteAdapter(R.id.widget_list_view, serviceIntent)
            views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_view)

            // 2. 리스트 아이템 클릭 시 '체크' 처리를 위한 템플릿 설정
            val clickIntent = Intent(context, TodoWidgetProvider::class.java).apply {
                action = ACTION_CHECK
            }
            val clickPendingIntent = PendingIntent.getBroadcast(
                context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list_view, clickPendingIntent)

            // 3. 새로고침 버튼 설정
            val refreshIntent = Intent(context, TodoWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId + 100, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_refresh, refreshPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, TodoWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        // ⭐ 위젯에서 체크박스/항목을 클릭했을 때 처리
        if (intent.action == ACTION_CHECK) {
            val todoId = intent.getLongExtra("todo_id", -1L)
            if (todoId != -1L) {
                updateTodoStatus(context, todoId)
            }
            // 데이터 변경 알림 (Factory의 onDataSetChanged 호출됨)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list_view)
        }

        // 새로고침 액션 처리
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list_view)
        }
    }

    // SharedPreferences 데이터를 직접 수정하는 함수
    private fun updateTodoStatus(context: Context, todoId: Long) {
        val prefs = context.getSharedPreferences("todo_app_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("todo_list", null) ?: return
        val type = object : TypeToken<MutableList<Todo>>() {}.type
        val list: MutableList<Todo> = Gson().fromJson(json, type) ?: return

        val index = list.indexOfFirst { it.id == todoId }
        if (index != -1) {
            val todo = list[index]
            list[index] = todo.copy(isCompleted = !todo.isCompleted)
            prefs.edit().putString("todo_list", Gson().toJson(list)).apply()
        }
    }
}