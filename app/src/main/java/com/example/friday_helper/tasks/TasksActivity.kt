package com.example.friday_helper.tasks

import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.friday_helper.R
import com.example.friday_helper.settings.ThemeApplier
import com.example.friday_helper.settings.ThemeManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.graphics.Color
import androidx.core.widget.doAfterTextChanged

class TasksActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_TASK_ID = "open_task_id"
        private const val TAG_FILTER_NO_TAG = "__NO_TAG__"
    }

    private var selectedDate = Calendar.getInstance(Locale.getDefault())
    private lateinit var repository: TasksRepository
    private val taskItems = mutableListOf<TaskItem>()
    private val allTaskItems = mutableListOf<TaskItem>()
    private lateinit var adapter: TasksAdapter
    private var selectedDayStartMillis: Long = 0
    private var calendarExpanded = true
    private val dateFormatSelected = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
    private val monthNames = arrayOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    )
    private val dateFormatList = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
    private var calendarBuildToken = 0
    private var currentSearchQuery: String = ""
    private var selectedTagFilter: String? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val openTaskId = intent.getLongExtra(EXTRA_OPEN_TASK_ID, -1L)
        if (openTaskId >= 0L) {
            intent.removeExtra(EXTRA_OPEN_TASK_ID)
            lifecycleScope.launch { openTaskById(openTaskId) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_tasks)

        ThemeManager.init(this)
        val root = findViewById<androidx.coordinatorlayout.widget.CoordinatorLayout>(R.id.tasksRoot)
        ThemeManager.theme.observe(this) { config ->
            val lighterBg = ThemeApplier.surfaceColorFor(
                ThemeApplier.surfaceColorFor(config.backgroundColor)
            )
            ThemeApplier.applyBackground(root, lighterBg, config.backgroundModifier)
            val textColor = if (config.backgroundColor == Color.WHITE) Color.BLACK else Color.WHITE
            findViewById<TextView>(R.id.tasksDateRange).setTextColor(textColor)
            findViewById<TextView>(R.id.tasksEmpty).setTextColor(textColor)
            findViewById<TextView>(R.id.tasksSearchCount).setTextColor(textColor)
            findViewById<ImageButton>(R.id.tasksBtnSearch).imageTintList = ColorStateList.valueOf(textColor)
            findViewById<ImageButton>(R.id.tasksBtnMenu).imageTintList = ColorStateList.valueOf(textColor)
        }
        ThemeManager.theme.value?.let { cfg ->
            val lighterBg = ThemeApplier.surfaceColorFor(
                ThemeApplier.surfaceColorFor(cfg.backgroundColor)
            )
            ThemeApplier.applyBackground(root, lighterBg, cfg.backgroundModifier)
            val textColor = if (cfg.backgroundColor == Color.WHITE) Color.BLACK else Color.WHITE
            findViewById<TextView>(R.id.tasksDateRange).setTextColor(textColor)
            findViewById<TextView>(R.id.tasksEmpty).setTextColor(textColor)
            findViewById<TextView>(R.id.tasksSearchCount).setTextColor(textColor)
            findViewById<ImageButton>(R.id.tasksBtnSearch).imageTintList = ColorStateList.valueOf(textColor)
            findViewById<ImageButton>(R.id.tasksBtnMenu).imageTintList = ColorStateList.valueOf(textColor)
        }

        repository = TasksRepository(TasksDatabase.getInstance(this))

        findViewById<View>(R.id.tasksBtnBack).setOnClickListener { finish() }
        setupSearchUi()
        findViewById<ImageButton>(R.id.tasksBtnMenu).setOnClickListener { v ->
            showTagFilterMenu(v)
        }
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.tasksFab).setOnClickListener {
            openTaskDialog(null, null)
        }

        selectedDayStartMillis = repository.dayStartMillis(selectedDate)
        updateDateHeader()
        buildMonthCalendar()
        setupCalendarCollapse()
        setupRecycler()
        requestNotificationPermissionIfNeeded()
        loadTasks()
        val openTaskId = intent.getLongExtra(EXTRA_OPEN_TASK_ID, -1L)
        if (openTaskId >= 0L) {
            intent.removeExtra(EXTRA_OPEN_TASK_ID)
            lifecycleScope.launch { openTaskById(openTaskId) }
        }
    }

    private fun setupCalendarCollapse() {
        val header = findViewById<View>(R.id.tasksCalendarHeader)
        val container = findViewById<View>(R.id.tasksCalendarContainer)
        val icon = findViewById<android.widget.ImageView>(R.id.tasksCalendarExpandIcon)

        fun toggleCalendar() {
            calendarExpanded = !calendarExpanded
            container.visibility = if (calendarExpanded) View.VISIBLE else View.GONE
            icon.rotation = if (calendarExpanded) 180f else 0f
        }

        // Сворачивание/разворачивание только по кнопке-иконке.
        header.setOnClickListener(null)
        header.isClickable = false
        header.isFocusable = false
        icon.setOnClickListener { toggleCalendar() }

        container.visibility = if (calendarExpanded) View.VISIBLE else View.GONE
        icon.rotation = if (calendarExpanded) 180f else 0f
    }

    private fun updateDateHeader() {
        findViewById<TextView>(R.id.tasksDateRange).text =
            dateFormatSelected.format(selectedDate.time).replaceFirstChar { it.uppercase() }
        val monthText = "${monthNames[selectedDate.get(Calendar.MONTH)]} ${selectedDate.get(Calendar.YEAR)}"
        findViewById<TextView>(R.id.tasksMonthYear).text = monthText
    }

    private fun buildMonthCalendar() {
        val token = ++calendarBuildToken
        val grid = findViewById<android.widget.GridLayout>(R.id.tasksMonthGrid)
        grid.removeAllViews()
        val year = selectedDate.get(Calendar.YEAR)
        val month0 = selectedDate.get(Calendar.MONTH)
        val firstDayOfWeek = Calendar.getInstance(Locale.getDefault()).firstDayOfWeek
        val dp = resources.displayMetrics.density
        val cellSize = (32 * dp).toInt()
        val margin = (2 * dp).toInt()

        val cal = Calendar.getInstance(Locale.getDefault()).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month0)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val monthStart = repository.dayStartMillis(cal)
        val monthEndCal = cal.clone() as Calendar
        monthEndCal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val monthEnd = repository.dayStartMillis(monthEndCal)
        lifecycleScope.launch {
            val daysWithTasks = withContext(Dispatchers.IO) {
                repository.getTaskDayStartsInRange(monthStart, monthEnd)
            }
            if (token != calendarBuildToken) return@launch
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        var firstWeekday = cal.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek
        if (firstWeekday < 0) firstWeekday += 7
        var cellIndex = 0
        for (i in 0 until firstWeekday) {
            val empty = TextView(this@TasksActivity).apply { text = "" }
            val lp = android.widget.GridLayout.LayoutParams().apply {
                width = cellSize
                height = cellSize
                setMargins(margin, margin, margin, margin)
                rowSpec = android.widget.GridLayout.spec(cellIndex / 7)
                columnSpec = android.widget.GridLayout.spec(cellIndex % 7)
            }
            grid.addView(empty, lp)
            cellIndex++
        }
        for (day in 1..daysInMonth) {
            val dayCal = Calendar.getInstance(Locale.getDefault()).apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month0)
                set(Calendar.DAY_OF_MONTH, day)
            }
            val dayStart = repository.dayStartMillis(dayCal)
            val isSelected = (dayStart == selectedDayStartMillis)
            val cell = LayoutInflater.from(this@TasksActivity)
                .inflate(R.layout.item_calendar_day_cell, grid, false) as LinearLayout
            val dayText = cell.findViewById<TextView>(R.id.dayCellText)
            val dayDot = cell.findViewById<View>(R.id.dayCellDot)
            dayText.text = day.toString()
            cell.setBackgroundResource(
                if (isSelected) R.drawable.bg_calendar_day_selected else R.drawable.bg_calendar_day
            )
            dayDot.visibility = if (dayStart in daysWithTasks) View.VISIBLE else View.GONE
            val lp = android.widget.GridLayout.LayoutParams().apply {
                width = cellSize
                height = cellSize
                setMargins(margin, margin, margin, margin)
                rowSpec = android.widget.GridLayout.spec(cellIndex / 7)
                columnSpec = android.widget.GridLayout.spec(cellIndex % 7)
            }
            cell.setOnClickListener {
                selectedDate = dayCal.clone() as Calendar
                selectedDayStartMillis = dayStart
                updateDateHeader()
                buildMonthCalendar()
                loadTasks()
            }
            grid.addView(cell, lp)
            cellIndex++
        }

        findViewById<View>(R.id.tasksMonthPrev).setOnClickListener {
            selectedDate.add(Calendar.MONTH, -1)
            selectedDayStartMillis = repository.dayStartMillis(selectedDate)
            updateDateHeader()
            buildMonthCalendar()
            loadTasks()
        }
        findViewById<View>(R.id.tasksMonthNext).setOnClickListener {
            selectedDate.add(Calendar.MONTH, 1)
            selectedDayStartMillis = repository.dayStartMillis(selectedDate)
            updateDateHeader()
            buildMonthCalendar()
            loadTasks()
        }
        }
    }

    private fun setupRecycler() {
        val recycler = findViewById<RecyclerView>(R.id.tasksRecycler)
        val emptyView = findViewById<TextView>(R.id.tasksEmpty)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = TasksAdapter(
            items = taskItems,
            onTaskDone = { taskId -> lifecycleScope.launch { toggleTaskDone(taskId) } },
            onSubtaskDone = { subtaskId -> lifecycleScope.launch { toggleSubtaskDone(subtaskId) } },
            onEdit = { item ->
                lifecycleScope.launch {
                    val tws = withContext(Dispatchers.IO) { repository.getTaskWithSubtasks(item.id) }
                    withContext(Dispatchers.Main) { openTaskDialog(item, tws?.task) }
                }
            },
            onDelete = { confirmDeleteTask(it) },
            onExpandChanged = { }
        )
        recycler.adapter = adapter
        emptyView.visibility = View.GONE
    }

    private fun setupSearchUi() {
        val searchBtn = findViewById<ImageButton>(R.id.tasksBtnSearch)
        val searchWrap = findViewById<View>(R.id.tasksSearchWrap)
        val searchInput = findViewById<EditText>(R.id.tasksSearchInput)
        searchBtn.setOnClickListener {
            val show = searchWrap.visibility != View.VISIBLE
            searchWrap.visibility = if (show) View.VISIBLE else View.GONE
            if (show) {
                searchInput.requestFocus()
            } else {
                searchInput.setText("")
            }
        }
        searchInput.doAfterTextChanged {
            currentSearchQuery = it?.toString().orEmpty()
            loadTasks()
        }
    }

    private fun applySearchFilter() {
        val emptyView = findViewById<TextView>(R.id.tasksEmpty)
        val searchCount = findViewById<TextView>(R.id.tasksSearchCount)
        val q = currentSearchQuery.trim().lowercase(Locale.getDefault())
        val filteredByTag = allTaskItems.filter { item ->
            when (selectedTagFilter) {
                null -> true
                TAG_FILTER_NO_TAG -> item.tags.isEmpty()
                else -> item.tags.contains(selectedTagFilter)
            }
        }
        val filtered = if (q.isBlank()) filteredByTag else filteredByTag.filter { item ->
            item.title.lowercase(Locale.getDefault()).contains(q) ||
                item.subtitle.lowercase(Locale.getDefault()).contains(q) ||
                item.tags.any { it.lowercase(Locale.getDefault()).contains(q) } ||
                item.subtasks.any { it.title.lowercase(Locale.getDefault()).contains(q) }
        }

        taskItems.clear()
        taskItems.addAll(filtered)
        adapter.submitList(taskItems.map { item ->
            if (q.isBlank() && selectedTagFilter == null) {
                item
            } else {
                val dateText = if (item.dueDateMillis > 0L) {
                    dateFormatList.format(java.util.Date(item.dueDateMillis))
                        .replaceFirstChar { it.uppercase() }
                } else {
                    "Без даты"
                }
                item.copy(title = "$dateText\n${item.title}")
            }
        })
        emptyView.visibility = if (taskItems.isEmpty()) View.VISIBLE else View.GONE
        val hasAnyFilter = q.isNotBlank() || selectedTagFilter != null
        searchCount.visibility = if (hasAnyFilter) View.VISIBLE else View.GONE
        if (hasAnyFilter) {
            searchCount.text = getString(R.string.tasks_search_result_count, taskItems.size)
        }
    }

    private fun showTagFilterMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val tags = listOf(
            getString(R.string.tasks_tag_work),
            getString(R.string.tasks_tag_personal),
            getString(R.string.tasks_tag_important),
            getString(R.string.tasks_tag_study),
            getString(R.string.tasks_tag_budget)
        )
        val itemAll = 1
        val itemNoTag = 2
        popup.menu.add(0, itemAll, 0, getString(R.string.tasks_filter_none))
        popup.menu.add(0, itemNoTag, 1, getString(R.string.tasks_filter_no_tag))
        tags.forEachIndexed { index, tag ->
            popup.menu.add(0, 100 + index, 10 + index, tag)
        }
        popup.setOnMenuItemClickListener { mi ->
            selectedTagFilter = when (mi.itemId) {
                itemAll -> null
                itemNoTag -> TAG_FILTER_NO_TAG
                else -> tags.getOrNull(mi.itemId - 100)
            }
            loadTasks()
            true
        }
        popup.show()
    }

    private suspend fun toggleTaskDone(taskId: Long) {
        val tws = repository.getTaskWithSubtasks(taskId) ?: return
        repository.toggleTaskDone(tws.task)
        loadTasks()
    }

    private suspend fun toggleSubtaskDone(subtaskId: Long) {
        val all = repository.getTasksForDay(selectedDayStartMillis)
        for (tws in all) {
            val sub = tws.subtasks.find { it.id == subtaskId } ?: continue
            repository.toggleSubtaskDone(sub)
            break
        }
        loadTasks()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }

    private fun loadTasks(onLoaded: (() -> Unit)? = null) {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                if (currentSearchQuery.trim().isBlank() && selectedTagFilter == null) {
                    repository.getTasksForDay(selectedDayStartMillis)
                } else {
                    repository.getAllTasksWithSubtasks()
                }
            }
            taskItems.clear()
            taskItems.addAll(list.map { tws ->
                TaskItem(
                    id = tws.task.id,
                    dueDateMillis = tws.task.dueDateMillis,
                    title = tws.task.title,
                    subtitle = tws.task.subtitle,
                    tags = if (tws.task.tags.isEmpty()) emptyList() else tws.task.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    done = tws.task.done,
                    cardColorIndex = tws.task.cardColorIndex,
                    subtasks = tws.subtasks.map { SubtaskItem(it.id, it.title, it.done) }
                )
            })
            allTaskItems.clear()
            allTaskItems.addAll(taskItems)
            applySearchFilter()
            onLoaded?.invoke()
        }
    }

    private suspend fun openTaskById(taskId: Long) {
        val tws = withContext(Dispatchers.IO) { repository.getTaskWithSubtasks(taskId) } ?: return
        withContext(Dispatchers.Main) {
            selectedDate = Calendar.getInstance(Locale.getDefault()).apply { timeInMillis = tws.task.dueDateMillis }
            selectedDayStartMillis = repository.dayStartMillis(selectedDate)
            updateDateHeader()
            buildMonthCalendar()
        }
        loadTasks {
            val item = taskItems.find { it.id == taskId }
            if (item != null) openTaskDialog(item, tws.task)
        }
    }

    private var dialogTimeMinutes: Int = 0
    private var dialogReminderType: String = REMINDER_NONE
    private var dialogReminderParam: Long = 0L

    private fun openTaskDialog(existing: TaskItem?, taskEntity: TaskEntity?) {
        val view = layoutInflater.inflate(R.layout.dialog_task_edit, null)
        val titleEdit = view.findViewById<EditText>(R.id.dialogTaskTitle)
        val subtitleEdit = view.findViewById<EditText>(R.id.dialogTaskSubtitle)
        val tagWork = view.findViewById<CheckBox>(R.id.dialogTagWork)
        val tagPersonal = view.findViewById<CheckBox>(R.id.dialogTagPersonal)
        val tagImportant = view.findViewById<CheckBox>(R.id.dialogTagImportant)
        val tagStudy = view.findViewById<CheckBox>(R.id.dialogTagStudy)
        val tagBudget = view.findViewById<CheckBox>(R.id.dialogTagBudget)
        val subtasksContainer = view.findViewById<LinearLayout>(R.id.dialogSubtasksList)
        val timeBtn = view.findViewById<android.widget.Button>(R.id.dialogTaskTime)
        val reminderSpinner = view.findViewById<Spinner>(R.id.dialogReminder)
        val reminderNWrap = view.findViewById<TextInputLayout>(R.id.dialogReminderNWrap)
        val reminderNEdit = view.findViewById<EditText>(R.id.dialogReminderN)

        val reminderOptions = listOf(
            REMINDER_NONE to getString(R.string.tasks_reminder_none),
            REMINDER_ONCE to getString(R.string.tasks_reminder_once),
            REMINDER_DAILY to getString(R.string.tasks_reminder_daily),
            REMINDER_EVERY_2_DAYS to getString(R.string.tasks_reminder_every_2_days),
            REMINDER_EVERY_3_DAYS to getString(R.string.tasks_reminder_every_3_days),
            REMINDER_EVERY_WEEK to getString(R.string.tasks_reminder_every_week),
            REMINDER_EVERY_N_DAYS to getString(R.string.tasks_reminder_every_n_days),
            REMINDER_EVERY_N_HOURS to getString(R.string.tasks_reminder_every_n_hours)
        )
        reminderSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, reminderOptions.map { it.second })
        reminderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                dialogReminderType = reminderOptions[position].first
                reminderNWrap.visibility = if (dialogReminderType == REMINDER_EVERY_N_DAYS || dialogReminderType == REMINDER_EVERY_N_HOURS) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        fun updateTimeButton() {
            timeBtn.text = if (dialogTimeMinutes == 0) getString(R.string.tasks_time_none)
            else String.format(Locale.getDefault(), "%02d:%02d", dialogTimeMinutes / 60, dialogTimeMinutes % 60)
        }
        timeBtn.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                dialogTimeMinutes = h * 60 + m
                updateTimeButton()
            }, dialogTimeMinutes / 60, dialogTimeMinutes % 60, true).show()
        }

        if (taskEntity != null) {
            dialogTimeMinutes = taskEntity.dueTimeMinutes
            dialogReminderType = taskEntity.reminderType
            dialogReminderParam = taskEntity.reminderParam
            updateTimeButton()
            val idx = reminderOptions.indexOfFirst { it.first == dialogReminderType }.coerceAtLeast(0)
            reminderSpinner.setSelection(idx)
            if (dialogReminderParam > 0) reminderNEdit.setText(dialogReminderParam.toString())
        } else {
            dialogTimeMinutes = 0
            dialogReminderType = REMINDER_NONE
            dialogReminderParam = 0L
            updateTimeButton()
        }

        view.findViewById<View>(R.id.dialogAddSubtask).setOnClickListener { addSubtaskRow(subtasksContainer, "") }

        if (existing != null) {
            titleEdit.setText(existing.title)
            subtitleEdit.setText(existing.subtitle)
            existing.tags.forEach { tag ->
                when (tag) {
                    getString(R.string.tasks_tag_work) -> tagWork.isChecked = true
                    getString(R.string.tasks_tag_personal) -> tagPersonal.isChecked = true
                    getString(R.string.tasks_tag_important) -> tagImportant.isChecked = true
                    getString(R.string.tasks_tag_study) -> tagStudy.isChecked = true
                    getString(R.string.tasks_tag_budget) -> tagBudget.isChecked = true
                }
            }
            existing.subtasks.forEach { addSubtaskRow(subtasksContainer, it.title) }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) getString(R.string.tasks_add_task) else getString(R.string.tasks_edit_task))
            .setView(view)
            .setPositiveButton(getString(R.string.action_save)) { _, _ ->
                val title = titleEdit.text.toString().trim()
                if (title.isEmpty()) return@setPositiveButton
                val subtitle = subtitleEdit.text.toString().trim()
                val reminderType = reminderOptions.getOrNull(reminderSpinner.selectedItemPosition)?.first ?: REMINDER_NONE
                var reminderParam = 0L
                if (reminderType == REMINDER_EVERY_N_DAYS || reminderType == REMINDER_EVERY_N_HOURS) {
                    reminderParam = reminderNEdit.text.toString().toLongOrNull()?.coerceIn(1L, 365L) ?: 1L
                } else if (reminderType == REMINDER_ONCE) {
                    val cal = selectedDate.clone() as Calendar
                    cal.set(Calendar.HOUR_OF_DAY, dialogTimeMinutes / 60)
                    cal.set(Calendar.MINUTE, dialogTimeMinutes % 60)
                    reminderParam = cal.timeInMillis
                }
                val tags = buildList {
                    if (tagWork.isChecked) add(getString(R.string.tasks_tag_work))
                    if (tagPersonal.isChecked) add(getString(R.string.tasks_tag_personal))
                    if (tagImportant.isChecked) add(getString(R.string.tasks_tag_important))
                    if (tagStudy.isChecked) add(getString(R.string.tasks_tag_study))
                    if (tagBudget.isChecked) add(getString(R.string.tasks_tag_budget))
                }.joinToString(",")
                val subtaskTitles = (0 until subtasksContainer.childCount).mapNotNull { i ->
                    val row = subtasksContainer.getChildAt(i) as? ViewGroup ?: return@mapNotNull null
                    val et = (0 until row.childCount).mapNotNull { j -> row.getChildAt(j) as? EditText }.firstOrNull() ?: return@mapNotNull null
                    et.text.toString().trim().takeIf { it.isNotEmpty() }
                }
                lifecycleScope.launch {
                    if (existing == null) {
                        val task = TaskEntity(
                            title = title,
                            subtitle = subtitle,
                            dueDateMillis = selectedDayStartMillis,
                            dueTimeMinutes = dialogTimeMinutes,
                            tags = tags,
                            done = false,
                            cardColorIndex = taskItems.size % 4,
                            sortOrder = taskItems.size,
                            reminderType = reminderType,
                            reminderParam = reminderParam
                        )
                        val id = withContext(Dispatchers.IO) { repository.insertTask(task, subtaskTitles) }
                        TaskNotificationScheduler.schedule(this@TasksActivity, task.copy(id = id))
                    } else {
                        val tws = repository.getTaskWithSubtasks(existing.id) ?: return@launch
                        val newSubtasks = subtaskTitles.mapIndexed { idx, t ->
                            val existingSub = tws.subtasks.getOrNull(idx)
                            SubtaskEntity(taskId = existing.id, title = t, done = existingSub?.done ?: false, sortOrder = idx)
                        }
                        val updatedTask = tws.task.copy(
                                title = title,
                                subtitle = subtitle,
                                tags = tags,
                                dueTimeMinutes = dialogTimeMinutes,
                                reminderType = reminderType,
                                reminderParam = reminderParam
                            )
                            withContext(Dispatchers.IO) { repository.updateTask(updatedTask, newSubtasks) }
                            TaskNotificationScheduler.schedule(this@TasksActivity, updatedTask)
                        }
                    loadTasks()
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun addSubtaskRow(container: LinearLayout, initialText: String) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_dialog_subtask, container, false)
        row.findViewById<EditText>(R.id.subtaskEdit).setText(initialText)
        row.findViewById<ImageButton>(R.id.subtaskRemove).setOnClickListener { container.removeView(row) }
        container.addView(row)
    }

    private fun confirmDeleteTask(taskId: Long) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete_title))
            .setMessage(getString(R.string.confirm_delete_task))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                TaskNotificationScheduler.cancel(this, taskId)
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repository.deleteTask(taskId) }
                    loadTasks()
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }
}
