package com.aloharoha.focus

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TodoWidgetFactory(applicationContext)
    }
}

class TodoWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var todoList = mutableListOf<Todo>()
    private val SHARED_PREFS_NAME = "todo_app_prefs"
    private val TODO_LIST_KEY = "todo_list"

    override fun onDataSetChanged() {
        // 1. SharedPreferences에서 최신 데이터 다시 로드 (중요!)
        val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(TODO_LIST_KEY, null)
        val type = object : TypeToken<MutableList<Todo>>() {}.type
        val allTodos: List<Todo> = Gson().fromJson(json, type) ?: emptyList()

        // 2. 오늘 날짜 할 일만 필터링
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        todoList = allTodos.filter { it.date == today }.toMutableList()
    }

    override fun getViewAt(position: Int): RemoteViews {
        // 데이터가 아예 없을 때 안내 문구 표시
        if (todoList.isEmpty()) {
            val emptyViews = RemoteViews(context.packageName, R.layout.item_widget_todo)
            emptyViews.setTextViewText(R.id.widget_todo_title, "앱에서 할 일을 추가해 보세요")
            emptyViews.setViewVisibility(R.id.widget_todo_checkbox, View.GONE) // 체크박스 숨기기
            return emptyViews
        }

        val todo = todoList[position]
        val views = RemoteViews(context.packageName, R.layout.item_widget_todo)

        views.setTextViewText(R.id.widget_todo_title, todo.title)
        views.setViewVisibility(R.id.widget_todo_checkbox, View.VISIBLE)

        // 체크 상태 이미지 설정
        val checkImg = if (todo.isCompleted) android.R.drawable.checkbox_on_background
        else android.R.drawable.checkbox_off_background
        views.setImageViewResource(R.id.widget_todo_checkbox, checkImg)

        // 아이템 클릭 시 ID 전달 (MainActivity의 ACTION_CHECK 처리용)
        val fillInIntent = Intent().apply {
            putExtra("todo_id", todo.id)
        }
        views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)

        return views
    }

    override fun getCount(): Int {
        // 데이터가 없어도 문구를 보여줘야 하므로 최소 1개 반환
        return if (todoList.isEmpty()) 1 else todoList.size
    }

    // 나머지는 기본값 유지
    override fun onCreate() {}
    override fun onDestroy() { todoList.clear() }
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = if (todoList.isEmpty()) -1L else todoList[position].id
    override fun hasStableIds(): Boolean = true
}