package com.example.friday_helper.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.friday_helper.R

class GlobalSearchAdapter(
    private var accentColor: Int,
    private var mutedColor: Int,
    private val onItemClick: (GlobalSearchListItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<GlobalSearchListItem>()

    companion object {
        private const val TYPE_HEADER = 1
        private const val TYPE_ROW = 2
    }

    fun submit(list: List<GlobalSearchListItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun setColors(accent: Int, muted: Int) {
        accentColor = accent
        mutedColor = muted
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is GlobalSearchListItem.Header -> TYPE_HEADER
        else -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val v = inflater.inflate(R.layout.item_global_search_header, parent, false)
            HeaderVH(v)
        } else {
            val v = inflater.inflate(R.layout.item_global_search_row, parent, false)
            RowVH(v)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is GlobalSearchListItem.Header -> (holder as HeaderVH).bind(item, accentColor)
            is GlobalSearchListItem.ChatHit -> (holder as RowVH).bindChat(item, accentColor, mutedColor)
            is GlobalSearchListItem.NoteHit -> (holder as RowVH).bindNote(item, accentColor, mutedColor)
            is GlobalSearchListItem.TaskHit -> (holder as RowVH).bindTask(item, accentColor, mutedColor)
        }
        holder.itemView.setOnClickListener {
            if (items[position] !is GlobalSearchListItem.Header) {
                onItemClick(items[position])
            }
        }
    }

    private class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.globalSearchHeaderText)
        fun bind(item: GlobalSearchListItem.Header, accent: Int) {
            val res = when (item.section) {
                GlobalSearchListItem.Section.CHATS -> R.string.global_search_section_chats
                GlobalSearchListItem.Section.NOTES -> R.string.global_search_section_notes
                GlobalSearchListItem.Section.TASKS -> R.string.global_search_section_tasks
            }
            tv.setText(res)
            tv.setTextColor(accent)
        }
    }

    private class RowVH(view: View) : RecyclerView.ViewHolder(view) {
        private val typeLabel: TextView = view.findViewById(R.id.globalSearchRowType)
        private val title: TextView = view.findViewById(R.id.globalSearchRowTitle)
        private val subtitle: TextView = view.findViewById(R.id.globalSearchRowSubtitle)

        fun bindChat(item: GlobalSearchListItem.ChatHit, accent: Int, muted: Int) {
            typeLabel.setText(R.string.global_search_type_chat)
            typeLabel.setTextColor(muted)
            val chatTitle = item.row.chatTitle.ifBlank { itemView.context.getString(R.string.global_search_untitled_chat) }
            title.text = chatTitle
            title.setTextColor(accent)
            subtitle.text = GlobalSearchRepository.chatSubtitle(item.row)
            subtitle.setTextColor(muted)
        }

        fun bindNote(item: GlobalSearchListItem.NoteHit, accent: Int, muted: Int) {
            typeLabel.setText(R.string.global_search_type_note)
            typeLabel.setTextColor(muted)
            val t = item.title.ifBlank { itemView.context.getString(R.string.global_search_untitled_note) }
            title.text = t
            title.setTextColor(accent)
            subtitle.text = item.preview
            subtitle.setTextColor(muted)
        }

        fun bindTask(item: GlobalSearchListItem.TaskHit, accent: Int, muted: Int) {
            typeLabel.setText(R.string.global_search_type_task)
            typeLabel.setTextColor(muted)
            title.text = item.task.title.ifBlank { itemView.context.getString(R.string.global_search_untitled_task) }
            title.setTextColor(accent)
            subtitle.text = GlobalSearchRepository.taskSubtitle(item.task)
            subtitle.setTextColor(muted)
        }
    }
}
