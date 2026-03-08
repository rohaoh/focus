package com.aloharoha.focus

import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.*
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dionsegijn.konfetti.xml.KonfettiView
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SHARED_PREFS_NAME = "todo_app_prefs"
        private const val THEME_PREF_KEY = "selected_theme"
        private const val DATE_FORMAT = "yyyy-MM-dd"
        const val ALL_DAY_DATA = "하루 종일"
        const val ANYTIME_TEXT = "아무 때나"
        private val DAY_OF_WEEK_NAMES = arrayOf("일", "월", "화", "수", "목", "금", "토")
    }

    private lateinit var db: AppDatabase
    private lateinit var todoAdapter: TodoAdapter
    private lateinit var weeklyAdapter: WeeklyAdapter
    private lateinit var konfettiView: KonfettiView
    private val todoList = mutableListOf<Todo>()
    private var currentWeekOffset = 0
    private var selectedDate: String = ""
    private var isConfettiFired = false
    private var currentTheme: String = "Light"
    private var calendar = Calendar.getInstance()
    private val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())

    private lateinit var textMonthYear: TextView
    private lateinit var layoutHome: View
    private lateinit var layoutMore: View
    private lateinit var fabAddTodo: FloatingActionButton
    private lateinit var firestore: FirebaseFirestore
    private lateinit var textProgress: TextView
    private lateinit var emptyStateText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var rvWeeklyCalendar: RecyclerView
    private lateinit var layoutCalendar: View
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient
    // ⭐ Firebase 변수
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        val currentTheme = setupTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        // 1. 데이터 및 DB 초기화
        db = AppDatabase.getDatabase(this)
        selectedDate = sdf.format(Date())
        auth = Firebase.auth

// 이미 로그인 되어 있는지 확인
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // 💡 로그인이 안 되어 있으면 나중에 로그인 화면으로 보낼 거야!
            // 지금은 테스트를 위해 익명 로그인이라도 시켜볼까?
            signInAnonymously()
        } else {
            // 로그인 되어 있으면 이 사람의 이름표(UID)로 데이터 저장 준비!
            saveUserData(currentUser.uid)
        }
        // 2. 뷰 연결 및 나머지 로직 (기존과 동일)
        layoutHome = findViewById(R.id.layout_home)
        layoutCalendar = findViewById(R.id.layout_calendar)
        layoutMore = findViewById(R.id.layout_more)
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)

        initViews()

        bottomNav.setOnItemSelectedListener { item ->
            layoutHome.visibility = View.GONE
            layoutCalendar.visibility = View.GONE
            layoutMore.visibility = View.GONE
            when (item.itemId) {
                R.id.nav_home -> { layoutHome.visibility = View.VISIBLE; true }
                R.id.nav_calendar -> { layoutCalendar.visibility = View.VISIBLE; true }
                R.id.nav_more -> { layoutMore.visibility = View.VISIBLE; true }
                else -> false
            }
        }

        setupGridCalendar()

        findViewById<Button>(R.id.btn_theme_setting).setOnClickListener { showThemeSelectionDialog() }
        findViewById<Button>(R.id.btn_allowed_apps_setting).setOnClickListener {
            startActivity(Intent(this, AppSelectionActivity::class.java))
        }
        findViewById<Button>(R.id.btn_privacy_policy).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.notion.so/...")))
        }
        findViewById<ImageButton>(R.id.btn_prev_month).setOnClickListener { calendar.add(Calendar.MONTH, -1); setupGridCalendar() }
        findViewById<ImageButton>(R.id.btn_next_month).setOnClickListener { calendar.add(Calendar.MONTH, 1); setupGridCalendar() }

        setupWeeklyCalendar(getThemePointColor())
        setupRecyclerView()
        applyViewColors(currentTheme)
        setupSwipeToDelete()
        runSmartLogic()
        checkActiveTimer()
    }
    private fun checkActiveTimer() {
        val prefs = getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_running", false)) {
            val intent = Intent(this, TimerActivity::class.java).apply {
                putExtra("task_id", prefs.getLong("active_task_id", -1L))
                putExtra("todo_title", prefs.getString("active_task_title", "집중"))
            }
            startActivity(intent)
        }

    }
    fun onItemChecked(todo: Todo, isChecked: Boolean) {
        // 1. DB 업데이트 함수 호출
        updateTodoStatus(todo, isChecked)

        // 2. ⭐ currentList 대신 todoList 사용!
        val index = todoList.indexOf(todo)
        if (index != -1) {
            // 3. ⭐ todoAdapter를 통해서 notify 호출!
            todoAdapter.notifyItemChanged(index)
        }
    }
    private fun formatTotalTime(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            "${hours}시간 ${minutes}분"
        } else if (minutes > 0) {
            "${minutes}분 ${seconds}초"
        } else {
            "${seconds}초"
        }
    }
    private fun showTodoBottomSheet(date: String) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_calendar_bottom_sheet, null)

        val title = view.findViewById<TextView>(R.id.sheet_title)
        val rv = view.findViewById<RecyclerView>(R.id.rv_sheet_todo)

        title.text = "$date 할 일"

        lifecycleScope.launch {
            // 1. 해당 날짜의 할 일 목록 가져오기
            val list = withContext(Dispatchers.IO) { db.todoDao().getTodosByDate(date) }

            // 2. 어댑터 연결 (기존 todoAdapter 설정과 비슷하게!)
            rv.layoutManager = LinearLayoutManager(this@MainActivity)
            rv.adapter = TodoAdapter(
                todos = list.toMutableList(),
                onTodoClicked = { todo -> /* 타이머 시작 등 로직 */ },
                onTodoChecked = { todo, isChecked -> updateTodoStatus(todo, isChecked) },
                onTodoEditRequested = { todo -> showAddTodoDialog(todo) }
            )
        }

        dialog.setContentView(view)
        dialog.show()
    }


    private fun triggerConfetti() {
        // 파티 설정: 색상, 속도, 위치 등을 정해줘
        val party = Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
            position = Position.Relative(0.5, 0.3), // 화면 중앙 위쪽
            emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100)
        )
        konfettiView.start(party)
    }
    private fun initViews() {
        textMonthYear = findViewById(R.id.text_month_year)
        fabAddTodo = findViewById(R.id.fab_add_todo)
        textProgress = findViewById(R.id.text_todo_progress)
        emptyStateText = findViewById(R.id.empty_state_text)
        recyclerView = findViewById(R.id.todo_list_recycler_view)
        rvWeeklyCalendar = findViewById(R.id.rv_weekly_calendar)
        konfettiView = findViewById(R.id.konfettiView)

        findViewById<ImageButton>(R.id.btn_prev_week).setOnClickListener {
            currentWeekOffset--
            updateWeeklyCalendar(getThemePointColor())
        }

        findViewById<ImageButton>(R.id.btn_next_week).setOnClickListener {
            currentWeekOffset++
            updateWeeklyCalendar(getThemePointColor())
        }

        fabAddTodo.setOnClickListener { showAddTodoDialog(null) }
    }

    private fun setupWeeklyCalendar(pointColor: Int) {
        weeklyAdapter = WeeklyAdapter { date ->
            selectedDate = date
            updateWeeklyCalendar(pointColor)
            loadTodos(selectedDate)
        }
        rvWeeklyCalendar.apply {
            adapter = weeklyAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
        }
        updateWeeklyCalendar(pointColor)
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun startFocusMode(todo: Todo) {
        if (!hasUsageStatsPermission()) {
            AlertDialog.Builder(this)
                .setTitle("권한 설정")
                .setMessage("차단 기능을 위해 '사용정보 접근 권한'을 허용해주세요.")
                .setPositiveButton("이동") { _, _ ->
                    startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .setNegativeButton("취소", null)
                .show()
            return
        }

        val timerIntent = Intent(this, TimerActivity::class.java).apply {
            putExtra("todo_title", todo.title)
            putExtra("task_id", todo.id)
        }
        startActivity(timerIntent)
    }

    private fun updateWeeklyCalendar(pointColor: Int) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.WEEK_OF_YEAR, currentWeekOffset)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)

        val weekDays = List(7) {
            cal.time.also { cal.add(Calendar.DAY_OF_YEAR, 1) }
        }

        weeklyAdapter.setData(weekDays, selectedDate, pointColor)
        val headerSdf = SimpleDateFormat("yyyy년 M월", Locale.getDefault())
        textMonthYear.text = headerSdf.format(weekDays[0])
    }

    private fun setupRecyclerView() {
        // ⭐ 이 부분이 핵심이야! 4개의 인자를 모두 전달해야 해.
        todoAdapter = TodoAdapter(
            todos = todoList,
            onTodoClicked = { todo ->
                // 1. 플레이 버튼 클릭 시 타이머 실행
                val intent = Intent(this, TimerActivity::class.java).apply {
                    putExtra("task_id", todo.id)
                    putExtra("todo_title", todo.title)
                }
                startActivity(intent)
            },
            onTodoChecked = { todo, isChecked ->
                // 2. 체크박스 클릭 시 상태 업데이트
                updateTodoStatus(todo, isChecked)
            },
            onTodoEditRequested = { todo ->
                // 3. 배경 클릭 시 수정 다이얼로그 띄우기 (새로 추가됨!)
                showAddTodoDialog(todo)
            }
        )
        recyclerView.adapter = todoAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }
    private fun updateTodoStatus(todo: Todo, isChecked: Boolean) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // DB 데이터 갱신
                db.todoDao().update(todo.copy(isCompleted = isChecked))
            }
            // 전체 리스트 다시 불러와서 UI(집중시간 등) 갱신
            loadTodos(selectedDate)
        }
    }

    private fun runSmartLogic() {
        lifecycleScope.launch {
            val today = sdf.format(Date())
            withContext(Dispatchers.IO) {
                val allTodos = db.todoDao().getAllTodos()
                allTodos.forEach { todo ->
                    if (!todo.isCompleted && todo.date < today) {
                        db.todoDao().update(todo.copy(date = today))
                    }
                }
            }
            generateRepeatingTodos(selectedDate)
            loadTodos(selectedDate)
        }
    }

    private suspend fun generateRepeatingTodos(dateStr: String) {
        val dateObj = sdf.parse(dateStr) ?: return
        val cal = Calendar.getInstance().apply { time = dateObj }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK).toString()

        val repeatingBases = db.todoDao().getRepeatingBaseTodos()
        repeatingBases.forEach { base ->
            if (base.repeatDays.split(",").contains(dayOfWeek)) {
                val exists = db.todoDao().checkExists(base.repeatId, dateStr)
                if (exists == 0) {
                    db.todoDao().insert(base.copy(id = 0, date = dateStr, repeatDays = ""))
                }
            }
        }
    }

    private fun loadTodos(date: String) {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { db.todoDao().getTodosByDate(date) }
            todoList.clear()
            todoList.addAll(list)
            todoAdapter.notifyDataSetChanged()

            emptyStateText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            updateProgressText(list)
        }
    }

    private fun updateProgressText(list: List<Todo>) {
        if (list.isEmpty()) {
            textProgress.text = "할 일이 등록되지 않았습니다."
            return
        }

        // 1. 달성도 계산
        val completed = list.count { it.isCompleted }
        val percent = (completed.toFloat() / list.size * 100).toInt()

        // 2. ⭐ 전체 집중 시간 합산 (모든 할 일의 focusTime을 더함)
        val totalFocusSeconds = list.sumOf { it.focusTime }
        val timeString = formatTotalTime(totalFocusSeconds)

        // 3. 텍스트 설정 (달성도 + 전체 시간)
        textProgress.text = "오늘의 달성도: $percent% (총 집중: $timeString)"

        // 컨페티 로직 (기존 유지)
        if (percent == 100 && !isConfettiFired) {
            triggerConfetti()
            isConfettiFired = true
        } else if (percent < 100) {
            isConfettiFired = false
        }
    }

    private fun triggerFireworks() {
        val party = Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
            emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
            position = Position.Relative(0.5, 0.3)
        )
        konfettiView.start(party)
    }

    private fun setupSwipeToDelete() {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val todoToDelete = todoList[position]

                lifecycleScope.launch {
                    db.todoDao().delete(todoToDelete)
                    loadTodos(selectedDate)
                    updateWidget()

                    Snackbar.make(findViewById(R.id.main_layout), "삭제되었습니다", Snackbar.LENGTH_LONG)
                        .setAction("복구") {
                            lifecycleScope.launch {
                                db.todoDao().insert(todoToDelete)
                                loadTodos(selectedDate)
                                updateWidget()
                            }
                        }.show()
                }
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, s: Int, a: Boolean) {
                val itemView = vh.itemView
                val itemHeight = itemView.bottom - itemView.top
                val background = ColorDrawable(Color.parseColor("#FF5252"))
                background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                background.draw(c)

                val icon: Drawable? = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_menu_delete)
                icon?.let {
                    val iconMargin = (itemHeight - it.intrinsicHeight) / 2
                    val iconTop = itemView.top + iconMargin
                    val iconBottom = iconTop + it.intrinsicHeight
                    val iconLeft = itemView.right - iconMargin - it.intrinsicWidth
                    val iconRight = itemView.right - iconMargin
                    it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    it.setTint(Color.WHITE)
                    it.draw(c)
                }
                super.onChildDraw(c, rv, vh, dX, dY, s, a)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }

    private fun showAddTodoDialog(todoToEdit: Todo?) {
        val isEditing = todoToEdit != null
        val currentTheme = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE).getString(THEME_PREF_KEY, "Light") ?: "Light"
        val colors = ThemeColors.get(currentTheme)
        val isDark = currentTheme.contains("Dark") || currentTheme == "Ocean" || currentTheme == "Purple" || currentTheme == "Green"

        val v = layoutInflater.inflate(R.layout.dialog_add_todo, null)
        v.setBackgroundResource(if (isDark) R.drawable.rounded_dialog_bg_dark else R.drawable.rounded_dialog_bg)

        val dialog = AlertDialog.Builder(this).setView(v).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val titleView = v.findViewById<TextView>(R.id.dialog_title)
        val input = v.findViewById<EditText>(R.id.edit_text_todo_input)
        val timeBtn = v.findViewById<Button>(R.id.button_set_time)
        val repeatBtn = v.findViewById<Button>(R.id.button_set_repeat)
        val useTimerCheck = v.findViewById<CheckBox>(R.id.checkbox_use_timer)
        val addBtn = v.findViewById<Button>(R.id.button_add)
        val cancelBtn = v.findViewById<Button>(R.id.button_cancel)
        val deleteBtn = v.findViewById<Button>(R.id.button_delete)

        val textColor = Color.parseColor(colors.text)
        val pointColor = Color.parseColor(colors.point)

        titleView.setTextColor(textColor)
        input.setTextColor(textColor)
        input.setHintTextColor(Color.GRAY)
        addBtn.setTextColor(pointColor)
        timeBtn.setTextColor(pointColor)
        repeatBtn.setTextColor(pointColor)
        cancelBtn.setTextColor(Color.GRAY)

        var currentSelectedTime = todoToEdit?.time ?: ""
        var currentRepeatDays = todoToEdit?.repeatDays ?: ""

        if (isEditing) {
            titleView.text = "할 일 수정"
            input.setText(todoToEdit!!.title)
            useTimerCheck.isChecked = todoToEdit.useTimer
            addBtn.text = "수정"
            deleteBtn.visibility = View.VISIBLE
            timeBtn.text = if (currentSelectedTime == ALL_DAY_DATA) ALL_DAY_DATA else if (currentSelectedTime == "") ANYTIME_TEXT else currentSelectedTime
            updateRepeatButtonText(repeatBtn, currentRepeatDays)
        }

        timeBtn.setOnClickListener {
            showCustomTimePicker(currentSelectedTime) { newTime ->
                currentSelectedTime = newTime
                timeBtn.text = if (newTime == ALL_DAY_DATA) ALL_DAY_DATA else if (newTime == "") ANYTIME_TEXT else newTime
            }
        }

        repeatBtn.setOnClickListener {
            showRepeatDialog(currentRepeatDays) { newDays ->
                currentRepeatDays = newDays
                updateRepeatButtonText(repeatBtn, newDays)
            }
        }

        addBtn.setOnClickListener {
            val titleText = input.text.toString().trim()
            if (titleText.isNotEmpty()) {
                lifecycleScope.launch {
                    val repeatId = if (currentRepeatDays.isNotEmpty() && !isEditing) System.currentTimeMillis() else (todoToEdit?.repeatId ?: 0L)
                    val todo = if (isEditing) {
                        todoToEdit!!.copy(title = titleText, time = currentSelectedTime, repeatDays = currentRepeatDays, useTimer = useTimerCheck.isChecked)
                    } else {
                        Todo(title = titleText, date = selectedDate, time = currentSelectedTime, repeatDays = currentRepeatDays, repeatId = repeatId, useTimer = useTimerCheck.isChecked)
                    }
                    if (isEditing) db.todoDao().update(todo) else db.todoDao().insert(todo)
                    loadTodos(selectedDate)
                    updateWidget()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(this@MainActivity, "할 일을 입력해주세요", Toast.LENGTH_SHORT).show()
            }
        }

        deleteBtn.setOnClickListener {
            lifecycleScope.launch {
                db.todoDao().delete(todoToEdit!!)
                loadTodos(selectedDate)
                updateWidget()
                dialog.dismiss()
            }
        }
        cancelBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
    private fun getInstalledApps(): List<Pair<String, String>> {
        val apps = mutableListOf<Pair<String, String>>()
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in packages) {
            // 시스템 앱을 제외하고 싶다면 조건문을 추가할 수 있어
            val label = pm.getApplicationLabel(app).toString()
            val pkgName = app.packageName
            apps.add(label to pkgName)
        }
        return apps.sortedBy { it.first } // 이름순 정렬
    }
    private fun showAllowedAppsDialog() {
        val apps = getInstalledApps()
        val appNames = apps.map { it.first }.toTypedArray()
        val appPackages = apps.map { it.second }.toTypedArray()

        val prefs = getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        val savedApps = prefs.getStringSet("allowed_packages", emptySet()) ?: emptySet()

        val checkedItems = BooleanArray(apps.size) { i -> savedApps.contains(appPackages[i]) }
        val selectedApps = savedApps.toMutableSet()

        AlertDialog.Builder(this)
            .setTitle("허용할 앱 선택")
            .setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
                if (isChecked) selectedApps.add(appPackages[which])
                else selectedApps.remove(appPackages[which])
            }
            .setPositiveButton("저장") { _, _ ->
                prefs.edit().putStringSet("allowed_packages", selectedApps).apply()
                Toast.makeText(this, "설정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }
    private fun showCustomTimePicker(initialTime: String, onTimeSelected: (String) -> Unit) {
        val currentTheme = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE).getString(THEME_PREF_KEY, "Light") ?: "Light"
        val colors = ThemeColors.get(currentTheme)
        val isDark = currentTheme.contains("Dark") || currentTheme == "Ocean" || currentTheme == "Purple" || currentTheme == "Green"

        val v = layoutInflater.inflate(R.layout.dialog_time_picker, null)
        v.setBackgroundResource(if (isDark) R.drawable.rounded_dialog_bg_dark else R.drawable.rounded_dialog_bg)

        val dialog = AlertDialog.Builder(this).setView(v).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val hp = v.findViewById<NumberPicker>(R.id.picker_hour).apply { minValue = 0; maxValue = 23 }
        val mp = v.findViewById<NumberPicker>(R.id.picker_minute).apply { minValue = 0; maxValue = 59 }

        val textColor = Color.parseColor(colors.text)
        val pointColor = Color.parseColor(colors.point)

        v.findViewById<TextView>(R.id.dialog_time_title)?.setTextColor(textColor)
        v.findViewById<Button>(R.id.button_time_select).setTextColor(pointColor)
        v.findViewById<Button>(R.id.button_all_day).setTextColor(pointColor)
        v.findViewById<Button>(R.id.button_any_time).setTextColor(pointColor)
        v.findViewById<Button>(R.id.button_time_cancel).setTextColor(Color.GRAY)

        v.findViewById<Button>(R.id.button_time_select).setOnClickListener {
            onTimeSelected(String.format("%02d:%02d", hp.value, mp.value))
            dialog.dismiss()
        }
        v.findViewById<Button>(R.id.button_all_day).setOnClickListener { onTimeSelected(ALL_DAY_DATA); dialog.dismiss() }
        v.findViewById<Button>(R.id.button_any_time).setOnClickListener { onTimeSelected(""); dialog.dismiss() }
        v.findViewById<Button>(R.id.button_time_cancel).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showRepeatDialog(initialDays: String, onDaysSelected: (String) -> Unit) {
        val currentTheme = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE).getString(THEME_PREF_KEY, "Light") ?: "Light"
        val colors = ThemeColors.get(currentTheme)
        val isDark = currentTheme.contains("Dark") || currentTheme == "Ocean" || currentTheme == "Purple" || currentTheme == "Green"

        val v = layoutInflater.inflate(R.layout.dialog_repeat_picker, null)
        v.setBackgroundResource(if (isDark) R.drawable.rounded_dialog_bg_dark else R.drawable.rounded_dialog_bg)

        val dialog = AlertDialog.Builder(this).setView(v).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val textColor = Color.parseColor(colors.text)
        val pointColor = Color.parseColor(colors.point)

        v.findViewById<TextView>(R.id.dialog_repeat_title)?.setTextColor(textColor)
        v.findViewById<Button>(R.id.button_set_repeat).setTextColor(pointColor)
        v.findViewById<Button>(R.id.button_clear_repeat).setTextColor(pointColor)
        v.findViewById<Button>(R.id.button_repeat_cancel).setTextColor(Color.GRAY)

        val buttons = arrayOf(R.id.btn_sun, R.id.btn_mon, R.id.btn_tue, R.id.btn_wed, R.id.btn_thu, R.id.btn_fri, R.id.btn_sat)
        val selectedSet = initialDays.split(",").filter { it.isNotEmpty() }.toMutableSet()

        for (i in buttons.indices) {
            val btn = v.findViewById<Button>(buttons[i])
            val day = (i + 1).toString()
            if (selectedSet.contains(day)) {
                btn.setBackgroundResource(R.drawable.rounded_button_selected)
                btn.setTextColor(Color.WHITE)
            } else {
                btn.setBackgroundResource(R.drawable.rounded_button_unselected)
                btn.setTextColor(Color.GRAY)
            }
            btn.setOnClickListener {
                if (selectedSet.contains(day)) {
                    selectedSet.remove(day)
                    btn.setBackgroundResource(R.drawable.rounded_button_unselected)
                    btn.setTextColor(Color.GRAY)
                } else {
                    selectedSet.add(day)
                    btn.setBackgroundResource(R.drawable.rounded_button_selected)
                    btn.setTextColor(Color.WHITE)
                }
            }
        }

        v.findViewById<Button>(R.id.button_set_repeat).setOnClickListener {
            onDaysSelected(selectedSet.sorted().joinToString(","))
            dialog.dismiss()
        }
        v.findViewById<Button>(R.id.button_clear_repeat).setOnClickListener { onDaysSelected(""); dialog.dismiss() }
        v.findViewById<Button>(R.id.button_repeat_cancel).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun updateRepeatButtonText(btn: Button, days: String) {
        if (days.isEmpty()) { btn.text = "반복 안 함"; return }
        val names = days.split(",").map { DAY_OF_WEEK_NAMES[it.toInt() - 1] }
        btn.text = names.joinToString(", ") + " 반복"
    }

    private fun updateWidget() {
        val intent = Intent(this, TodoWidgetProvider::class.java).apply { action = AppWidgetManager.ACTION_APPWIDGET_UPDATE }
        val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, TodoWidgetProvider::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids); sendBroadcast(intent)
    }

    private fun setupTheme(): String {
        val prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val theme = prefs.getString(THEME_PREF_KEY, "Light") ?: "Light"
        setTheme(when (theme) {
            "Dark" -> R.style.Theme_Focus_Dark
            "Purple" -> R.style.Theme_Focus_Purple
            "Rose" -> R.style.Theme_Focus_Rose
            "Ocean" -> R.style.Theme_Focus_Ocean
            "Green" -> R.style.Theme_Focus_Green
            "Dark Orange" -> R.style.Theme_Focus_DarkOrange
            "Warm" -> R.style.Theme_Focus_Warm
            else -> R.style.Theme_Focus_Light
        })
        return theme
    }

    private fun applyViewColors(theme: String) {
        val colors = ThemeColors.get(theme)
        val pointColor = Color.parseColor(colors.point)
        val bgColor = Color.parseColor(colors.bg)
        val textColor = Color.parseColor(colors.text)

        // 1. 모든 탭 배경색 적용 (이게 확실히 돼야 글자가 보여!)
        layoutHome.setBackgroundColor(bgColor)
        layoutCalendar.setBackgroundColor(bgColor)
        layoutMore.setBackgroundColor(bgColor)

        // 2. 홈 탭 주간 달력 날짜 색상 해결!
        // setupWeeklyCalendar를 호출할 때 포인트 컬러를 넘겨줘서 날짜가 보이게 해!
        setupWeeklyCalendar(pointColor)

        // 3. 캘린더 탭 연/월 및 제목 색상
        findViewById<TextView>(R.id.text_month_year)?.setTextColor(textColor)
        findViewById<TextView>(R.id.tv_calendar_title)?.setTextColor(textColor)

        // 4. ⭐ 캘린더 탭 그리드뷰 강제 새로고침 ⭐
        // 어댑터에 바뀐 테마 색상(textColor)을 넘겨줘야 숫자가 하얗게(다크모드) 나와!
        setupGridCalendar()

        // 5. 하단 바 및 버튼 설정 (생략된 기존 코드 유지)
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
        bottomNav?.setBackgroundColor(pointColor)

        val buttons = listOf(R.id.btn_theme_setting, R.id.btn_allowed_apps_setting, R.id.btn_privacy_policy)
        buttons.forEach { id ->
            findViewById<Button>(id)?.apply {
                backgroundTintList = ColorStateList.valueOf(pointColor)
                setTextColor(Color.WHITE)
            }
        }
    }

    private fun getThemePointColor(): Int {
        val theme = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE).getString(THEME_PREF_KEY, "Light") ?: "Light"
        return Color.parseColor(ThemeColors.get(theme).point)
    }

    private fun showThemeSelectionDialog() {
        val items = arrayOf("라이트", "다크", "퍼플", "로즈", "오션", "그린", "다크 오렌지", "웜")
        val values = arrayOf("Light", "Dark", "Purple", "Rose", "Ocean", "Green", "Dark Orange", "Warm")
        AlertDialog.Builder(this).setTitle("테마 선택").setItems(items) { _, which ->
            val selectedTheme = values[which]
            getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(THEME_PREF_KEY, selectedTheme).apply()

            // ⭐ 위젯에게 테마 변경 알림!
            updateWidget()

            recreate() // 화면 다시 그리기
        }.show()
    }
    private fun setupGridCalendar() {
        val gridView = findViewById<GridView>(R.id.calendar_grid_view)
        val colors = ThemeColors.get(currentTheme)
        val textColor = Color.parseColor(colors.text)
        val pointColor = Color.parseColor(colors.point)

        // 1. 날짜 데이터 가져오기 (방금 만든 함수!)
        val days = getDaysInMonth(calendar)

        // 2. 어댑터에 색상 정보 팍팍 넘겨주기! ㅋ꙼̈ㅋ̆̎ㅋ̐̈ㅋ̊̈ㅋ̄̈
        // CalendarAdapter(context, 데이터, 포인트색, 글자색) 순서야!
        gridView.adapter = CalendarAdapter(this, days, pointColor, textColor)

        // 3. 타이틀 텍스트 색상도 잊지 마! ㆅㆅㆅㆅ
        val sdf = SimpleDateFormat("yyyy. MM", Locale.KOREAN)
        findViewById<TextView>(R.id.tv_calendar_title).apply {
            text = sdf.format(calendar.time)
            setTextColor(textColor)
        }
    }
    private fun getInstalledAppsWithIcons(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        val pm = packageManager // 앱 정보를 관리하는 매니저를 불러와

        // 폰에 설치된 모든 앱 정보를 가져와
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        for (app in packages) {
            // ⭐ 핵심: '실행 가능한 앱(런처에 보이는 앱)'만 골라내기!
            // 이 코드가 없으면 시스템 설정 같은 눈에 안 보이는 앱까지 다 나와서 지저분해져.
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                val label = pm.getApplicationLabel(app).toString() // 앱 이름 (예: 카카오톡)
                val icon = pm.getApplicationIcon(app) // 앱 아이콘 이미지
                val pkgName = app.packageName // 패키지명 (예: com.kakao.talk)

                // 아까 만든 AppInfo 바구니에 하나씩 담아줘
                apps.add(AppInfo(icon, label, pkgName))
            }
        }


        // 앱 이름순으로 예쁘게 정렬해서 돌려주기!
        return apps.sortedBy { it.appName }
    }
    private fun getDaysInMonth(calendar: Calendar): List<String> {
        val dayList = mutableListOf<String>()
        val tempCal = calendar.clone() as Calendar

        // 1. 이번 달의 1일로 설정 [cite: 2025-04-16]
        tempCal.set(Calendar.DAY_OF_MONTH, 1)

        // 2. 이번 달의 마지막 날짜(28, 30, 31일 등) 가져오기 [cite: 2025-04-16]
        val maxDay = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // 3. 1일부터 마지막 날까지 리스트에 넣기 ㅋ꙼̈ㅋ̆̎ㅋ̐̈ㅋ̊̈ㅋ̄̈
        for (i in 1..maxDay) {
            dayList.add(i.toString())
        }

        return dayList
    }
    // 1. 익명 로그인 함수 (테스트용으로 먼저 쓰기 좋아!)
    private fun signInAnonymously() {
        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // 로그인 성공!
                    val user = auth.currentUser
                    user?.let { saveUserData(it.uid) }
                    Toast.makeText(this, "익명 로그인 성공!", Toast.LENGTH_SHORT).show()
                } else {
                    // 로그인 실패
                    Toast.makeText(this, "로그인 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // 2. 사용자 데이터를 서버에 등록하는 함수 (로하의 2번 목표!)
    private fun saveUserData(userId: String) {
        val userMap = hashMapOf(
            "lastLogin" to com.google.firebase.Timestamp.now(),
            "nickname" to "로하야" // [cite: 2025-04-14]
        )

        // users 컬렉션에 사용자 UID 이름으로 문서를 만들어!
        firestore.collection("users").document(userId)
            .set(userMap)
            .addOnSuccessListener {
                android.util.Log.d("Firestore", "사용자 정보 등록 완료!")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("Firestore", "등록 에러: ${e.message}")
            }
    }
}
data class AppInfo(
    val icon: android.graphics.drawable.Drawable,
    val appName: String,
    val packageName: String,
    var isChecked: Boolean = false
)