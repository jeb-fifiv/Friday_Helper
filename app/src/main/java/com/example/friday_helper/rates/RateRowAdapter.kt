package com.example.friday_helper.rates
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.friday_helper.R

data class RateRow(
    val title: String,
    val subtitle: String,
    val value: String
)

class RateRowAdapter : RecyclerView.Adapter<RateRowAdapter.VH>() {

    private val items: MutableList<RateRow> = mutableListOf()
    private var textColor: Int = android.graphics.Color.BLACK
    private var fillColor: Int = android.graphics.Color.WHITE
    private var strokeColor: Int = android.graphics.Color.BLACK

    fun submit(list: List<RateRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun setStyle(text: Int, fill: Int, stroke: Int) {
        textColor = text
        fillColor = fill
        strokeColor = stroke
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_rate, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], textColor, fillColor, strokeColor)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val tvValue: TextView = itemView.findViewById(R.id.tvValue)

        fun bind(row: RateRow, color: Int, fill: Int, stroke: Int) {
            tvTitle.text = row.title
            tvSubtitle.text = row.subtitle
            tvValue.text = row.value
            tvTitle.setTextColor(color)
            tvSubtitle.setTextColor(color)
            tvValue.setTextColor(color)

            // Background card that follows theme (like in other screens)
            val d = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12f * itemView.resources.displayMetrics.density
                setColor(fill)
                val px = (2 * itemView.resources.displayMetrics.density).toInt().coerceAtLeast(2)
                setStroke(px, stroke)
            }
            itemView.background = d
        }
    }
}

