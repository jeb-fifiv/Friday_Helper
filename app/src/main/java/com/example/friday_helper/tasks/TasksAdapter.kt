package com.example.friday_helper.tasks

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.friday_helper.R

/** Модель для отображения в списке (с id для обновлений). */
data class TaskItem(
    val id: Long,
    val dueDateMillis: Long,
    val title: String,
    val subtitle: String,
    val tags: List<String>,
    val done: Boolean,
    val cardColorIndex: Int,
    val subtasks: List<SubtaskItem>
)
data class SubtaskItem(val id: Long, val title: String, val done: Boolean)

class TasksAdapter(
    private var items: MutableList<TaskItem>,
    private val onTaskDone: (Long) -> Unit,
    private val onSubtaskDone: (Long) -> Unit,
    private val onEdit: (TaskItem) -> Unit,
    private val onDelete: (Long) -> Unit,
    private val onExpandChanged: () -> Unit = {}
) : RecyclerView.Adapter<TasksAdapter.VH>() {

    fun submitList(list: List<TaskItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    private val cardColors by lazy {
        intArrayOf(
            R.color.tasks_card_yellow,
            R.color.tasks_card_blue,
            R.color.tasks_card_pink,
            R.color.tasks_card_gray
        )
    }
    private val tagColors by lazy {
        mapOf(
            "Работа" to R.color.tasks_tag_work,
            "Личное" to R.color.tasks_tag_personal,
            "Важно" to R.color.tasks_tag_important,
            "Учёба" to R.color.tasks_tag_study,
            "Бюджет" to R.color.tasks_tag_budget
        )
    }
    private val expanded = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        (holder.card as? com.google.android.material.card.MaterialCardView)?.setCardBackgroundColor(
            ContextCompat.getColor(ctx, cardColors[item.cardColorIndex % cardColors.size])
        )

        holder.checkbox.isChecked = item.done
        holder.checkbox.setOnCheckedChangeListener { _, _ -> onTaskDone(item.id) }

        holder.title.text = item.title
        holder.title.alpha = if (item.done) 0.6f else 1f
        holder.title.setTypeface(null, if (item.done) Typeface.NORMAL else Typeface.BOLD)

        holder.subtitle.text = item.subtitle
        holder.subtitle.alpha = if (item.done) 0.6f else 1f

        holder.menu.setOnClickListener { v ->
            val popup = PopupMenu(v.context, v)
            popup.menu.add(0, 0, 0, ctx.getString(R.string.tasks_edit_task))
            popup.menu.add(0, 1, 0, ctx.getString(R.string.tasks_delete_task))
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    0 -> onEdit(item)
                    1 -> onDelete(item.id)
                }
                true
            }
            popup.show()
        }

        holder.subtasksContainer.removeAllViews()
        val isExpanded = expanded.contains(position)
        if (item.subtasks.isNotEmpty()) {
            holder.expand.visibility = View.VISIBLE
            holder.expand.rotation = if (isExpanded) 180f else 0f
            holder.expand.setOnClickListener {
                if (expanded.contains(position)) expanded.remove(position)
                else expanded.add(position)
                notifyItemChanged(position)
                onExpandChanged()
            }
            if (isExpanded) {
                holder.subtasksContainer.visibility = View.VISIBLE
                item.subtasks.forEach { sub ->
                    val subView = LayoutInflater.from(ctx).inflate(R.layout.item_subtask, holder.subtasksContainer, false)
                    subView.findViewById<TextView>(R.id.subtaskTitle).text = sub.title
                    subView.findViewById<TextView>(R.id.subtaskTitle).alpha = if (sub.done) 0.6f else 1f
                    val subCb = subView.findViewById<CheckBox>(R.id.subtaskCheckbox)
                    subCb.isChecked = sub.done
                    subCb.tag = sub.id
                    subCb.setOnCheckedChangeListener { _, _ -> onSubtaskDone(sub.id) }
                    holder.subtasksContainer.addView(subView)
                }
            } else {
                holder.subtasksContainer.visibility = View.GONE
            }
        } else {
            holder.expand.visibility = View.GONE
            holder.subtasksContainer.visibility = View.GONE
        }

        holder.tags.removeAllViews()
        item.tags.forEach { tag ->
            val chip = LayoutInflater.from(ctx).inflate(android.R.layout.simple_list_item_1, holder.tags, false) as TextView
            chip.text = tag
            chip.setPadding(
                ctx.resources.getDimensionPixelSize(R.dimen.tasks_tag_padding_h),
                ctx.resources.getDimensionPixelSize(R.dimen.tasks_tag_padding_v),
                ctx.resources.getDimensionPixelSize(R.dimen.tasks_tag_padding_h),
                ctx.resources.getDimensionPixelSize(R.dimen.tasks_tag_padding_v)
            )
            chip.textSize = 11f
            chip.setBackgroundResource(R.drawable.bg_calendar_day)
            chip.setTextColor(ContextCompat.getColor(ctx, tagColors[tag] ?: R.color.tasks_header_secondary))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = ctx.resources.getDimensionPixelSize(R.dimen.tasks_calendar_cell_margin)
            holder.tags.addView(chip, lp)
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: View = itemView.findViewById(R.id.taskCard)
        val checkbox: CheckBox = itemView.findViewById(R.id.taskCheckbox)
        val title: TextView = itemView.findViewById(R.id.taskTitle)
        val subtitle: TextView = itemView.findViewById(R.id.taskSubtitle)
        val expand: ImageButton = itemView.findViewById(R.id.taskExpand)
        val menu: ImageButton = itemView.findViewById(R.id.taskMenu)
        val tags: LinearLayout = itemView.findViewById(R.id.taskTags)
        val subtasksContainer: LinearLayout = itemView.findViewById(R.id.taskSubtasksContainer)
    }
}
