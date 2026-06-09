package com.example.friday_helper.chat

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.friday_helper.MainActivity
import com.example.friday_helper.R
import com.example.friday_helper.settings.ThemeApplier
import com.example.friday_helper.settings.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatsActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var adapter: ChatListAdapter
    private lateinit var chatRepository: ChatRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chats)

        val toolbar: com.google.android.material.appbar.MaterialToolbar = findViewById(R.id.toolbar)
        val root = findViewById<android.view.View>(R.id.rootChats)
        val recycler: RecyclerView = findViewById(R.id.chatsRecycler)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        toolbar.title = ""
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        ThemeManager.init(this)
        ThemeManager.theme.observe(this) { cfg ->
            cfg?.let {
                ThemeApplier.applyToToolbar(toolbar, it)
                ThemeApplier.applyBackground(root, it)
                ThemeApplier.applyFont(root, this, it.fontResId)
                val txtColor = it.accentColor
                findViewById<TextView>(R.id.tvTitle).setTextColor(txtColor)
                adapter.setColors(txtColor)
            }
        }

        chatRepository = ChatRepository(AppDatabase.getInstance(this))
        adapter = ChatListAdapter(
            items = emptyList(),
            onChatClick = { chat: ChatEntity ->
                startActivity(Intent(this, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_OPEN_CHAT_ID, chat.id)
                })
                finish()
            },
            onChatLongClick = { chat: ChatEntity ->
                showDeleteChatDialog(chat)
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        loadChats()
    }

    private fun showDeleteChatDialog(chat: ChatEntity) {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete_chat)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                scope.launch {
                    chatRepository.deleteChat(chat.id)
                    loadChats()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun loadChats() {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                chatRepository.getChatsList()
            }
            adapter.submitList(list)
        }
    }
}

class ChatListAdapter(
    private var items: List<ChatEntity>,
    private val onChatClick: (ChatEntity) -> Unit,
    private val onChatLongClick: (ChatEntity) -> Unit,
    private var textColor: Int = 0
) : RecyclerView.Adapter<ChatListAdapter.VH>() {

    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    fun submitList(list: List<ChatEntity>) {
        items = list
        notifyDataSetChanged()
    }

    fun setColors(color: Int) {
        textColor = color
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val chat = items[position]
        holder.title.text = chat.title.ifEmpty { "Новый чат" }
        holder.date.text = dateFormat.format(Date(chat.createdAt))
        if (textColor != 0) {
            holder.title.setTextColor(textColor)
            holder.date.setTextColor(textColor)
        }
        holder.itemView.setOnClickListener { onChatClick(chat) }
        holder.itemView.setOnLongClickListener {
            onChatLongClick(chat)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.chatTitle)
        val date: TextView = itemView.findViewById(R.id.chatDate)
    }
}
