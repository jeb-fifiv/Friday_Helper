package com.example.friday_helper.chat

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.friday_helper.R

/** Статус последнего сообщения пользователя для индикатора галочек: 0 = только в приложении, 1 = ИИ обрабатывает, 2 = ИИ готовит/отправил ответ */
const val MESSAGE_STATUS_LOCAL = 0
const val MESSAGE_STATUS_PROCESSING = 1
const val MESSAGE_STATUS_ANSWERED = 2

class MessageAdapter(
    private var items: List<MessageEntity>,
    private var accentColor: Int,
    private var backgroundColor: Int,
    private var lastUserMessageStatus: Int = MESSAGE_STATUS_ANSWERED
) : RecyclerView.Adapter<MessageAdapter.VH>() {

    var onMessageActionClick: ((MessageEntity, Int, MessageAction) -> Unit)? = null
    private var selectedPosition: Int = -1

    enum class MessageAction { RESTART, COPY, DELETE }

    fun submitList(list: List<MessageEntity>, lastUserStatus: Int = MESSAGE_STATUS_ANSWERED) {
        items = list
        lastUserMessageStatus = lastUserStatus
        notifyDataSetChanged()
    }

    /** Обновляет только текст последнего сообщения (для стриминга без полной перерисовки списка). */
    fun updateLastMessageContent(content: String) {
        if (items.isEmpty()) return
        val last = items.last()
        items = items.dropLast(1) + last.copy(content = content)
        notifyItemChanged(items.size - 1)
    }

    fun setTheme(accent: Int, background: Int) {
        accentColor = accent
        backgroundColor = background
        notifyDataSetChanged()
    }

    fun selectMessage(position: Int) {
        val old = selectedPosition
        selectedPosition = if (selectedPosition == position) -1 else position
        if (old >= 0) notifyItemChanged(old)
        if (selectedPosition >= 0) notifyItemChanged(selectedPosition)
    }

    fun clearSelection() {
        val old = selectedPosition
        selectedPosition = -1
        if (old >= 0) notifyItemChanged(old)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = items[position]
        holder.text.text = msg.content
        holder.itemView.setOnLongClickListener {
            selectMessage(position)
            true
        }
        val isUser = msg.role == "user"
        (holder.text.layoutParams as? FrameLayout.LayoutParams)?.gravity = if (isUser) {
            android.view.Gravity.END
        } else {
            android.view.Gravity.START
        }
        (holder.bubbleWrap.layoutParams as? LinearLayout.LayoutParams)?.gravity = if (isUser) {
            android.view.Gravity.END
        } else {
            android.view.Gravity.START
        }
        (holder.actionsBar.layoutParams as? LinearLayout.LayoutParams)?.gravity = if (isUser) {
            android.view.Gravity.END
        } else {
            android.view.Gravity.START
        }
        holder.text.setBackgroundResource(
            if (isUser) R.drawable.bg_message_bubble_user else R.drawable.bg_message_bubble_assistant
        )
        holder.text.setTextColor(if (isUser) Color.DKGRAY else Color.WHITE)
        holder.actionsBar.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE
        holder.actionCopy.setOnClickListener {
            onMessageActionClick?.invoke(msg, position, MessageAction.COPY)
        }
        holder.actionRestart.setOnClickListener {
            onMessageActionClick?.invoke(msg, position, MessageAction.RESTART)
        }
        holder.actionDelete.setOnClickListener {
            onMessageActionClick?.invoke(msg, position, MessageAction.DELETE)
        }

        val ctx = holder.itemView.context
        val checkGray = ContextCompat.getColor(ctx, R.color.message_check_gray)
        val checkBlue = ContextCompat.getColor(ctx, R.color.message_check_blue)
        if (!isUser) {
            holder.checkRow.visibility = View.GONE
        } else {
            holder.checkRow.visibility = View.VISIBLE
            val lastUserIndex = items.indexOfLast { it.role == "user" }
            val status = if (position == lastUserIndex) lastUserMessageStatus else MESSAGE_STATUS_ANSWERED
            holder.check1.setColorFilter(if (status == MESSAGE_STATUS_LOCAL) checkGray else checkBlue)
            holder.check1.visibility = View.VISIBLE
            if (status >= MESSAGE_STATUS_ANSWERED) {
                holder.check2.visibility = View.VISIBLE
                holder.check2.setColorFilter(checkBlue)
            } else {
                holder.check2.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.messageText)
        val bubbleWrap: View = itemView.findViewById(R.id.messageBubbleWrap)
        val actionsBar: View = itemView.findViewById(R.id.messageActionsBar)
        val actionCopy: TextView = itemView.findViewById(R.id.actionCopyText)
        val actionRestart: TextView = itemView.findViewById(R.id.actionRestartText)
        val actionDelete: TextView = itemView.findViewById(R.id.actionDeleteText)
        val checkRow: View = itemView.findViewById(R.id.messageCheckRow)
        val check1: ImageView = itemView.findViewById(R.id.messageCheck1)
        val check2: ImageView = itemView.findViewById(R.id.messageCheck2)
    }
}
