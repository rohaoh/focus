package com.aloharoha.focus

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class WeeklyAdapter(private val onDateSelected: (String) -> Unit) : RecyclerView.Adapter<WeeklyAdapter.DayViewHolder>() {

    private val days = mutableListOf<Date>()
    private var selectedDate = ""
    private var pointColor = Color.BLUE
    private var textColor = Color.BLACK
    private var isDark = false

    private val dSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val nSdf = SimpleDateFormat("E", Locale.getDefault())
    private val numSdf = SimpleDateFormat("d", Locale.getDefault())

    fun setData(newDays: List<Date>, selected: String, color: Int) {
        days.clear()
        days.addAll(newDays)
        selectedDate = selected
        pointColor = color
        notifyDataSetChanged()
    }

    fun updateTheme(color: Int, point: Int, dark: Boolean) {
        this.textColor = color
        this.pointColor = point
        this.isDark = dark
        notifyDataSetChanged()
    }

    inner class DayViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val name = v.findViewById<TextView>(R.id.text_day_name)
        val num = v.findViewById<TextView>(R.id.text_day_number)
        val layout = v.findViewById<View>(R.id.day_layout)

        fun bind(date: Date) {
            val dateStr = dSdf.format(date)
            name.text = nSdf.format(date)
            num.text = numSdf.format(date)

            // 요일 이름 색상 (항상 회색톤 유지 혹은 테마에 맞춤)
            name.setTextColor(if (isDark) Color.parseColor("#AAAAAA") else Color.parseColor("#888888"))

            if (dateStr == selectedDate) {
                // 선택된 날짜: 포인트 색상 배경 + 흰색 글씨
                num.setTextColor(Color.WHITE)
                num.backgroundTintList = ColorStateList.valueOf(pointColor)
                num.setBackgroundResource(R.drawable.rounded_button_selected)
            } else {
                // 선택되지 않은 날짜: 테마별 기본 글씨색
                num.setTextColor(textColor)
                num.background = null
            }

            layout.setOnClickListener {
                onDateSelected(dateStr)
            }
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int): DayViewHolder {
        val v = LayoutInflater.from(p.context).inflate(R.layout.item_day, p, false)
        return DayViewHolder(v)
    }

    override fun onBindViewHolder(h: DayViewHolder, p: Int) {
        h.bind(days[p])
    }

    override fun getItemCount() = days.size
}