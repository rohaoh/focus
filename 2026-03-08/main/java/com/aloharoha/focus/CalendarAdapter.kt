package com.aloharoha.focus

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class CalendarAdapter(
    private val context: Context,
    private val dateList: List<String>, // "1", "2", ... "31" 또는 빈칸
    private val pointColor: Int,
    private val textColor: Int
) : BaseAdapter() {

    override fun getCount(): Int = dateList.size
    override fun getItem(position: Int): Any = dateList[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_calendar_day, parent, false)

        val tvDay = view.findViewById<TextView>(R.id.tv_day)
        val indicator = view.findViewById<View>(R.id.view_todo_indicator)

        val day = dateList[position]
        tvDay.text = day
        tvDay.setTextColor(textColor) // 테마 글자색 적용! ㅋ꙼̈ㅋ̆̎ㅋ̐̈ㅋ̊̈ㅋ̄̈

        // 만약 오늘 날짜라면 포인트 색상으로 동그라미 치거나 글자색 바꾸기!
        // (간단하게 일단 글자색만 바꿔볼게!)
        // if (isToday(day)) tvDay.setTextColor(pointColor)

        return view
    }
}