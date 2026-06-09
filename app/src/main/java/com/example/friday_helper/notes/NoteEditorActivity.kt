package com.example.friday_helper.notes

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.friday_helper.R
import com.example.friday_helper.databinding.ActivityNoteEditorBinding
import com.example.friday_helper.settings.ThemeApplier
import com.example.friday_helper.settings.ThemeManager
import com.google.android.material.appbar.MaterialToolbar
import com.example.friday_helper.security.SecurityRepository
import com.example.friday_helper.security.SecuritySession
import com.example.friday_helper.security.LockActivity
import android.content.Intent

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditorBinding
    private lateinit var repo: NotesRepository
    private var editingId: Long? = null
    private var lastSavedHash: Int = 0
    private var currentFolderId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sec = SecurityRepository(this)
        if (SecuritySession.isGuest) {
            android.widget.Toast.makeText(this, "Гостевой режим: заметки недоступны", android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (sec.isNotesProtectionEnabled() && sec.isPinEnabled() && !SecuritySession.isUnlocked) {
            startActivity(Intent(this, LockActivity::class.java))
            finish()
            return
        }

        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = ""
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        binding.btnBackEditor.setOnClickListener { finish() }
        findViewById<android.widget.ImageButton>(R.id.btnSaveEditor).setOnClickListener {
            if (saveIfNeeded()) {
                android.widget.Toast.makeText(this, R.string.action_save, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        repo = NotesRepository(this)
        editingId = if (intent.hasExtra(EXTRA_NOTE_ID)) intent.getLongExtra(EXTRA_NOTE_ID, -1L).takeIf { it > 0 } else null
        if (editingId != null) {
            val note = repo.getNotes().find { it.id == editingId }
            if (note != null) {
                binding.etTitle.setText(note.title)
                binding.etText.setText(note.text)
                currentFolderId = note.folderId
            }
        }

        val originalTitleHint = getString(R.string.hint_note_title)
        val originalTextHint = getString(R.string.hint_note_text)
        binding.etTitle.setOnFocusChangeListener { v, hasFocus ->
            val et = v as com.google.android.material.textfield.TextInputEditText
            binding.titleInputLayout.hint = if (hasFocus) "" else if (et.text?.isEmpty() != false) originalTitleHint else ""
        }
        binding.etText.setOnFocusChangeListener { v, hasFocus ->
            val et = v as com.google.android.material.textfield.TextInputEditText
            binding.textInputLayout.hint = if (hasFocus) "" else if (et.text?.isEmpty() != false) originalTextHint else ""
        }
        // Also hide hint on typing
        binding.etTitle.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty()) binding.titleInputLayout.hint = ""
            }
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s.isNullOrEmpty() && !binding.etTitle.hasFocus()) binding.titleInputLayout.hint = originalTitleHint
            }
        })
        binding.etText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty()) binding.textInputLayout.hint = ""
            }
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s.isNullOrEmpty() && !binding.etText.hasFocus()) binding.textInputLayout.hint = originalTextHint
            }
        })

        ThemeManager.init(this)
        ThemeManager.theme.observe(this) { cfg ->
            ThemeApplier.applyToToolbar(toolbar, cfg)
            ThemeApplier.applyBackground(binding.rootEditor, cfg)
            ThemeApplier.applyFont(binding.rootEditor, this, cfg.fontResId)
            val surface = ThemeApplier.surfaceColorFor(cfg.backgroundColor)
            ThemeApplier.styleRoundedBackground(binding.titleInputLayout.background, surface, cfg.accentColor)
            ThemeApplier.styleRoundedBackground(binding.textInputLayout.background, surface, cfg.accentColor)
            ThemeApplier.styleTextInput(binding.titleInputLayout, cfg)
            ThemeApplier.styleTextInput(binding.textInputLayout, cfg)
        }
    }

    override fun onPause() {
        super.onPause()
        saveIfNeeded()
    }

    // Кнопка сохранения перенесена в верхнюю панель (btnSaveEditor)

    private fun saveIfNeeded(): Boolean {
        val title = binding.etTitle.text?.toString()?.trim().orEmpty()
        val text = binding.etText.text?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return false
        val contentHash = (title + "\n" + text).hashCode()
        if (contentHash == lastSavedHash) return false
        val id = editingId ?: System.currentTimeMillis()
        repo.addOrUpdateNote(
            Note(
                id = id,
                title = title,
                text = text,
                folderId = currentFolderId
            )
        )
        editingId = id
        lastSavedHash = contentHash
        return true
    }

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
    }
}

