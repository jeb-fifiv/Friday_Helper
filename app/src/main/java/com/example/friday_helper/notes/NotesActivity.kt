package com.example.friday_helper.notes

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.friday_helper.R
import com.example.friday_helper.databinding.ActivityNotesBinding
import com.example.friday_helper.settings.ThemeApplier
import com.example.friday_helper.settings.ThemeManager
import com.google.android.material.appbar.MaterialToolbar
import com.example.friday_helper.security.SecurityRepository
import com.example.friday_helper.security.SecuritySession
import com.example.friday_helper.security.LockActivity

class NotesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotesBinding
    private lateinit var repo: NotesRepository
    private lateinit var adapter: NotesListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Notes protection / guest mode
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

        binding = ActivityNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = ""
        // Навигацию через собственную кнопку ниже
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        repo = NotesRepository(this)
        adapter = NotesListAdapter(
            onNoteClick = { note ->
                val intent = Intent(this, NoteEditorActivity::class.java)
                intent.putExtra(NoteEditorActivity.EXTRA_NOTE_ID, note.id)
                startActivity(intent)
            },
            onDeleteNote = { note ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.confirm_delete_title)
                    .setMessage(R.string.confirm_delete_note)
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        repo.deleteNote(note.id)
                        reload()
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            },
            onDeleteFolder = { folder ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.confirm_delete_title)
                    .setMessage(R.string.confirm_delete_folder)
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        repo.deleteFolder(folder.id)
                        reload()
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            },
            onMoveNoteToFolder = { noteId, folderId ->
                val note = repo.getNotes().find { it.id == noteId } ?: return@NotesListAdapter
                repo.addOrUpdateNote(note.copy(folderId = folderId))
                adapter.setExpanded(folderId, true)
                reload()
            }
        )
        binding.recyclerNotes.layoutManager = LinearLayoutManager(this)
        binding.recyclerNotes.adapter = adapter
        // Ensure overlay buttons are above list
        binding.fabMain.bringToFront()
        binding.btnCreateFolder.bringToFront()
        binding.btnCreateNote.bringToFront()

        binding.fabMain.setOnClickListener { toggleCreateButtons() }
        binding.btnCreateFolder.setOnClickListener {
            toggleCreateButtons(show = false)
            promptCreateFolder()
        }
        binding.btnCreateNote.setOnClickListener {
            toggleCreateButtons(show = false)
            val intent = Intent(this, NoteEditorActivity::class.java)
            startActivity(intent)
        }

        // Фон берём из основной темы, но делаем заметно светлее; карточки остаются белыми
        ThemeManager.init(this)
        ThemeManager.theme.observe(this) { cfg ->
            val lighterBg = ThemeApplier.surfaceColorFor(
                ThemeApplier.surfaceColorFor(cfg.backgroundColor)
            )
            ThemeApplier.applyBackground(binding.rootNotes, lighterBg, cfg.backgroundModifier)
            val textOnBg = cfg.accentColor
            val surface = ThemeApplier.surfaceColorFor(cfg.backgroundColor)
            val darkBg = textOnBg == android.graphics.Color.WHITE
            val btnFill = if (darkBg) surface else android.graphics.Color.WHITE
            val btnStroke = if (darkBg) cfg.accentColor else android.graphics.Color.BLACK
            // Принудительно задаём фон как GradientDrawable, чтобы не зависеть от стиля MaterialButton
            fun applyBtnBg(view: View) {
                val d = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 12f * view.resources.displayMetrics.density
                    setColor(btnFill)
                    val px = (2 * view.resources.displayMetrics.density).toInt().coerceAtLeast(2)
                    setStroke(px, btnStroke)
                }
                view.background = d
            }
            applyBtnBg(binding.btnCreateFolder)
            applyBtnBg(binding.btnCreateNote)
            binding.emptyView.setTextColor(textOnBg)
            findViewById<android.widget.TextView>(R.id.tvMyNotes)?.setTextColor(textOnBg)
            // Кнопки под тему: белый текст на тёмном, чёрный на светлом
            binding.btnCreateFolder.setTextColor(textOnBg)
            binding.btnCreateNote.setTextColor(textOnBg)
            // Убираем материал-тон, чтобы не было сиреневого
            binding.btnCreateFolder.backgroundTintList = null
            binding.btnCreateNote.backgroundTintList = null
        }
        // FAB всегда чёрный по фону
        binding.fabMain.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK)
        // Поставим иконку plus, если есть
        runCatching {
            val id = resources.getIdentifier("plus", "drawable", packageName)
            if (id != 0) binding.fabMain.setImageResource(id)
        }
        // Текст/иконки элементов на белых карточках — чёрные
        adapter.updateColors(text = android.graphics.Color.BLACK, icon = android.graphics.Color.BLACK)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val folders = repo.getFolders()
        val notes = repo.getNotes()
        adapter.setData(folders, notes)
        binding.emptyView.visibility = if (folders.isEmpty() && notes.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggleCreateButtons(show: Boolean? = null) {
        val shouldShow = show ?: (binding.btnCreateFolder.visibility != View.VISIBLE)
        val alphaStart = if (shouldShow) 0f else 1f
        val alphaEnd = if (shouldShow) 1f else 0f
        listOf(binding.btnCreateFolder, binding.btnCreateNote).forEach { v ->
            if (shouldShow) {
                v.alpha = alphaStart
                v.visibility = View.VISIBLE
                v.animate().alpha(alphaEnd).setDuration(120).start()
            } else {
                v.animate().alpha(alphaEnd).setDuration(120).withEndAction {
                    v.visibility = View.GONE
                    v.alpha = 1f
                }.start()
            }
        }
    }

    private fun promptCreateFolder() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = getString(R.string.dialog_folder_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_folder_title)
            .setView(input)
            .setPositiveButton(R.string.action_save) { _: DialogInterface, _: Int ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    repo.addFolder(name)
                    reload()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}

private sealed class ListItem {
    data class FolderItem(val folder: Folder) : ListItem()
    data class NoteItem(val note: Note, val parentFolderId: Long?) : ListItem()
}

private class NotesListAdapter(
    private val onNoteClick: (Note) -> Unit,
    private val onDeleteNote: (Note) -> Unit,
    private val onDeleteFolder: (Folder) -> Unit,
    private val onMoveNoteToFolder: (noteId: Long, folderId: Long) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {

    private val data = mutableListOf<ListItem>()
    private var textColor: Int = android.graphics.Color.BLACK
    private var iconTint: Int = android.graphics.Color.BLACK
    private var folders: List<Folder> = emptyList()
    private var notes: List<Note> = emptyList()
    private val expanded: MutableSet<Long> = mutableSetOf()

    fun setData(folders: List<Folder>, notes: List<Note>) {
        this.folders = folders
        this.notes = notes
        rebuild()
    }

    fun updateColors(text: Int, icon: Int) {
        textColor = text
        iconTint = icon
        notifyDataSetChanged()
    }

    fun setExpanded(folderId: Long, value: Boolean) {
        if (value) expanded.add(folderId) else expanded.remove(folderId)
    }

    private fun rebuild() {
        data.clear()
        folders.forEach { folder ->
            data.add(ListItem.FolderItem(folder))
            if (expanded.contains(folder.id)) {
                notes.filter { it.folderId == folder.id }.forEach { n ->
                    data.add(ListItem.NoteItem(n, parentFolderId = folder.id))
                }
            }
        }
        notes.filter { it.folderId == null }.forEach { n ->
            data.add(ListItem.NoteItem(n, parentFolderId = null))
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (data[position]) {
        is ListItem.FolderItem -> 0
        is ListItem.NoteItem -> 1
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
        when (val item = data[position]) {
            is ListItem.FolderItem -> (holder as FolderVH).bind(item.folder)
            is ListItem.NoteItem -> (holder as NoteVH).bind(item.note, indent = item.parentFolderId != null)
        }
    }

    private class FolderVH(
        itemView: android.view.View,
        private val onDelete: (Folder) -> Unit,
        private val getIconTint: () -> Int,
        private val getNotes: () -> List<Note>,
        private val isExpanded: (Long) -> Boolean,
        private val toggleExpanded: (Long) -> Unit,
        private val onMoveNoteToFolder: (noteId: Long, folderId: Long) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        private val name: android.widget.TextView = itemView.findViewById(R.id.tvFolderName)
        private val count: android.widget.TextView = itemView.findViewById(R.id.tvFolderCount)
        private val icon: android.widget.ImageView = itemView.findViewById(R.id.ivFolderIcon)
        private val btnDelete: android.widget.ImageButton = itemView.findViewById(R.id.btnDeleteFolder)
        private var current: Folder? = null
        init {
            itemView.setOnClickListener {
                current?.let { f -> toggleExpanded(f.id) }
            }
            btnDelete.setOnClickListener { current?.let(onDelete) }
            itemView.setOnDragListener { v, event ->
                when (event.action) {
                    android.view.DragEvent.ACTION_DRAG_STARTED -> true
                    android.view.DragEvent.ACTION_DRAG_ENTERED -> { v.alpha = 0.85f; true }
                    android.view.DragEvent.ACTION_DRAG_EXITED -> { v.alpha = 1f; true }
                    android.view.DragEvent.ACTION_DROP -> {
                        v.alpha = 1f
                        val idText = event.clipData?.getItemAt(0)?.text?.toString()
                        val noteId = idText?.toLongOrNull()
                        val folderId = current?.id
                        if (noteId != null && folderId != null) {
                            onMoveNoteToFolder(noteId, folderId)
                        }
                        true
                    }
                    android.view.DragEvent.ACTION_DRAG_ENDED -> { v.alpha = 1f; true }
                    else -> false
                }
            }
        }
        fun bind(folder: Folder) {
            current = folder
            name.text = folder.name
            val adapter = (itemView.parent as? androidx.recyclerview.widget.RecyclerView)?.adapter as? NotesListAdapter
            name.setTextColor(adapter?.textColor ?: android.graphics.Color.BLACK)
            btnDelete.imageTintList = android.content.res.ColorStateList.valueOf(getIconTint())
            val c = getNotes().count { it.folderId == folder.id }
            count.text = "($c)"
            count.setTextColor(adapter?.textColor ?: android.graphics.Color.BLACK)
            name.setTypeface(name.typeface, if (isExpanded(folder.id)) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            // Set folder icon by state
            val ctx = itemView.context
            val opened = isExpanded(folder.id)
            val resName = when {
                c == 0 -> "closed_folder_without"
                opened -> "open_folder_with"
                else -> "closed_folder_with"
            }
            val id = ctx.resources.getIdentifier(resName, "drawable", ctx.packageName)
            if (id != 0) icon.setImageResource(id)
        }
    }

    private class NoteVH(
        itemView: android.view.View,
        private val onClick: (Note) -> Unit,
        private val onDelete: (Note) -> Unit,
        private val getIconTint: () -> Int
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        private val title: android.widget.TextView = itemView.findViewById(R.id.tvNoteTitle)
        private val snippet: android.widget.TextView = itemView.findViewById(R.id.tvNoteSnippet)
        private val content: android.view.View = itemView.findViewById(R.id.noteContent)
        private val icon: android.widget.ImageView = itemView.findViewById(R.id.ivNoteIcon)
        private val btnDelete: android.widget.ImageButton = itemView.findViewById(R.id.btnDeleteNote)
        private var current: Note? = null
        init {
            itemView.setOnClickListener { current?.let(onClick) }
            btnDelete.setOnClickListener { current?.let(onDelete) }
            itemView.setOnLongClickListener {
                current?.let { n ->
                    val data = android.content.ClipData.newPlainText("note_id", n.id.toString())
                    val shadow = android.view.View.DragShadowBuilder(itemView)
                    itemView.startDragAndDrop(data, shadow, null, 0)
                    true
                } ?: false
            }
        }
        fun bind(note: Note, indent: Boolean) {
            current = note
            val t = note.title.ifBlank { "" }
            title.text = if (t.isNotEmpty()) t else itemView.context.getString(R.string.hint_note_title)
            val firstLine = note.text.trim().lineSequence().firstOrNull().orEmpty()
            snippet.text = firstLine
            snippet.visibility = if (firstLine.isEmpty()) View.GONE else View.VISIBLE
            val adapter = (itemView.parent as? androidx.recyclerview.widget.RecyclerView)?.adapter as? NotesListAdapter
            val color = adapter?.textColor ?: android.graphics.Color.BLACK
            title.setTextColor(color)
            snippet.setTextColor(color)
            btnDelete.imageTintList = android.content.res.ColorStateList.valueOf(getIconTint())
            val d = itemView.resources.displayMetrics.density
            // В папке: отступ слева (входят в папку) и чуть уже по ширине
            (itemView.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { params ->
                params.marginStart = ((if (indent) 32 else 8) * d).toInt()
                params.marginEnd = ((if (indent) 20 else 8) * d).toInt()
                itemView.layoutParams = params
            }
            (content.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { params ->
                params.marginStart = ((if (indent) 8 else 0) * d).toInt()
                content.layoutParams = params
            }
            // Set note icon
            val ctx = itemView.context
            val id = ctx.resources.getIdentifier("file", "drawable", ctx.packageName)
            if (id != 0) icon.setImageResource(id)
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
        val inflater = android.view.LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            val v = inflater.inflate(R.layout.item_folder, parent, false)
            FolderVH(
                v,
                onDelete = onDeleteFolder,
                getIconTint = { iconTint },
                getNotes = { notes },
                isExpanded = { id -> expanded.contains(id) },
                toggleExpanded = { id -> if (!expanded.add(id)) expanded.remove(id); rebuild() },
                onMoveNoteToFolder = onMoveNoteToFolder
            )
        } else {
            val v = inflater.inflate(R.layout.item_note, parent, false)
            NoteVH(v, onNoteClick, onDeleteNote) { iconTint }
        }
    }
}

