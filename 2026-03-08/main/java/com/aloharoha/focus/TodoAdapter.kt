package com.aloharoha.focus

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class TodoAdapter(
    var todos: MutableList<Todo>,
    private val onTodoClicked: (Todo) -> Unit, // 플레이 버튼용 (타이머)
    private val onTodoChecked: (Todo, Boolean) -> Unit,
    private val onTodoEditRequested: (Todo) -> Unit // ⭐ 배경 클릭용 (수정) 추가
) : RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {

    private var textColor: Int = Color.BLACK
    private var pointColor: Int = Color.BLUE
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun updateList(newList: List<Todo>) {
        todos.clear()
        todos.addAll(newList)
        notifyDataSetChanged()
    }

    fun updateTheme(color: Int, point: Int) {
        this.textColor = color
        this.pointColor = point
        notifyDataSetChanged()
    }

    inner class TodoViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.todo_title)
        val time: TextView = v.findViewById(R.id.todo_time)
        val checkBox: CheckBox = v.findViewById(R.id.todo_checkbox)
        val btnStartFocus: ImageButton = v.findViewById(R.id.btn_start_focus)

        fun bind(todo: Todo) {
            title.text = todo.title
            title.setTextColor(textColor)

            val focusM = todo.focusTime / 60
            val focusS = todo.focusTime % 60
            val focusString = if (todo.focusTime > 0) "\n(누적 집중: ${focusM}분 ${focusS}초)" else ""

            time.text = (when (todo.time) {
                MainActivity.ALL_DAY_DATA -> MainActivity.ALL_DAY_DATA
                "" -> MainActivity.ANYTIME_TEXT
                else -> todo.time
            }) + focusString

            time.setTextColor(Color.GRAY)
            time.visibility = if (todo.time.isEmpty() && todo.focusTime == 0L) View.GONE else View.VISIBLE

            checkBox.buttonTintList = ColorStateList.valueOf(pointColor)

            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = todo.isCompleted

            // 집중 모드 버튼 표시 여부
            btnStartFocus.visibility = if (todo.useTimer && !todo.isCompleted) View.VISIBLE else View.GONE

            // ⭐ 1. 플레이 버튼 클릭 시 -> 타이머 실행
            btnStartFocus.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onTodoClicked(todos[pos])
                }
            }

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onTodoChecked(todos[pos], isChecked)
                    updateStrikeThrough(isChecked)
                }
            }

            // ⭐ 2. 아이템 배경 클릭 시 -> 수정 다이얼로그 띄우기
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onTodoEditRequested(todos[pos])
                }
            }

            updateStrikeThrough(todo.isCompleted)
            applyWeekendColor(title, todo.date)
        }

        private fun updateStrikeThrough(isCompleted: Boolean) {
            if (isCompleted) {
                title.paintFlags = title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                title.alpha = 0.5f
            } else {
                title.paintFlags = title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                title.alpha = 1.0f
            }
        }

        private fun applyWeekendColor(textView: TextView, dateString: String) {
            try {
                val dateObj = sdf.parse(dateString) ?: return
                val calendar = Calendar.getInstance().apply { time = dateObj }
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

                when (dayOfWeek) {
                    Calendar.SATURDAY -> textView.setTextColor(Color.parseColor("#448AFF"))
                    Calendar.SUNDAY -> textView.setTextColor(Color.parseColor("#FF5252"))
                    else -> textView.setTextColor(textColor)
                }
            } catch (e: Exception) {
                textView.setTextColor(textColor)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_todo, parent, false)
        return TodoViewHolder(v)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        holder.bind(todos[position])
    }

    override fun getItemCount(): Int = todos.size
}