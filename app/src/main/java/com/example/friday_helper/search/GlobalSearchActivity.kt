package com.example.friday_helper.search

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.friday_helper.MainActivity
import com.example.friday_helper.R
import com.example.friday_helper.notes.NoteEditorActivity
import com.example.friday_helper.settings.ThemeApplier
import com.example.friday_helper.settings.ThemeManager
import com.example.friday_helper.tasks.TasksActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GlobalSearchActivity : AppCompatActivity() {

    private var searchJob: Job? = null
    private lateinit var adapter: GlobalSearchAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_global_search)

        val toolbar = findViewById<MaterialToolbar>(R.id.globalSearchToolbar)
        val root = findViewById<View>(R.id.globalSearchRoot)
        val edit = findViewById<TextInputEditText>(R.id.globalSearchEdit)
        val inputLayout = findViewById<TextInputLayout>(R.id.globalSearchInputLayout)
        val recycler = findViewById<RecyclerView>(R.id.globalSearchRecycler)
        val empty = findViewById<TextView>(R.id.globalSearchEmpty)
        val titleTv = findViewById<TextView>(R.id.globalSearchTitle)
        val btnBack = findViewById<TextView>(R.id.globalSearchBtnBack)

        findViewById<View>(R.id.globalSearchBtnBack).setOnClickListener { finish() }

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        ThemeManager.init(this)

        val cfg0 = ThemeManager.theme.value
        val accent0 = cfg0?.accentColor ?: Color.BLACK
        val bg0 = cfg0?.backgroundColor ?: Color.WHITE
        val muted0 = ColorUtils.blendARGB(bg0, accent0, 0.42f)

        adapter = GlobalSearchAdapter(accent0, muted0) { item ->
            when (item) {
                is GlobalSearchListItem.ChatHit -> {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(MainActivity.EXTRA_OPEN_CHAT_ID, item.row.chatId)
                    })
                    finish()
                }
                is GlobalSearchListItem.NoteHit -> {
                    startActivity(Intent(this, NoteEditorActivity::class.java).apply {
                        putExtra(NoteEditorActivity.EXTRA_NOTE_ID, item.noteId)
                    })
                }
                is GlobalSearchListItem.TaskHit -> {
                    startActivity(Intent(this, TasksActivity::class.java).apply {
                        putExtra(TasksActivity.EXTRA_OPEN_TASK_ID, item.task.id)
                    })
                }
                else -> Unit
            }
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        fun applyTheme(cfg: ThemeManager.ThemeConfig) {
            ThemeApplier.applyToToolbar(toolbar, cfg)
            toolbar.background = ColorDrawable(
                ThemeApplier.topBarColorFor(cfg.backgroundColor, cfg.backgroundModifier)
            )
            ThemeApplier.applyBackground(root, cfg)
            ThemeApplier.applyFont(root, this, cfg.fontResId)
            val muted = ColorUtils.blendARGB(cfg.backgroundColor, cfg.accentColor, 0.42f)
            adapter.setColors(cfg.accentColor, muted)
            titleTv.setTextColor(cfg.accentColor)
            btnBack.setTextColor(cfg.accentColor)
            ThemeApplier.styleTextInput(inputLayout, cfg)
            edit.setTextColor(cfg.accentColor)
            empty.setTextColor(muted)
            window.statusBarColor = ThemeApplier.topBarColorFor(cfg.backgroundColor, cfg.backgroundModifier)
            window.navigationBarColor = ThemeApplier.bottomBarColorFor(cfg.backgroundColor, cfg.backgroundModifier)
            val controller = WindowInsetsControllerCompat(window, root)
            val r = Color.red(cfg.backgroundColor) / 255.0
            val g = Color.green(cfg.backgroundColor) / 255.0
            val b = Color.blue(cfg.backgroundColor) / 255.0
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            val darkBg = luminance < 0.5
            controller.isAppearanceLightStatusBars = !darkBg
            controller.isAppearanceLightNavigationBars = !darkBg
        }

        ThemeManager.theme.observe(this) { cfg ->
            if (cfg != null) applyTheme(cfg)
        }
        cfg0?.let { applyTheme(it) }

        fun updateEmptyState(query: String, resultCount: Int) {
            val q = query.trim()
            when {
                q.isEmpty() -> {
                    empty.setText(R.string.global_search_empty_hint)
                    empty.visibility = View.VISIBLE
                    recycler.visibility = View.GONE
                }
                resultCount == 0 -> {
                    empty.setText(R.string.global_search_no_results)
                    empty.visibility = View.VISIBLE
                    recycler.visibility = View.GONE
                }
                else -> {
                    empty.visibility = View.GONE
                    recycler.visibility = View.VISIBLE
                }
            }
        }

        fun runSearch(query: String) {
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                val q = query
                delay(300)
                val results = GlobalSearchRepository.search(this@GlobalSearchActivity, q)
                adapter.submit(results)
                updateEmptyState(q, results.size)
            }
        }

        edit.doAfterTextChanged { text ->
            runSearch(text?.toString().orEmpty())
        }

        edit.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchJob?.cancel()
                lifecycleScope.launch {
                    val q = v.text?.toString().orEmpty()
                    val results = GlobalSearchRepository.search(this@GlobalSearchActivity, q)
                    adapter.submit(results)
                    updateEmptyState(q, results.size)
                }
                true
            } else {
                false
            }
        }

        updateEmptyState("", 0)
        edit.post {
            edit.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(edit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
