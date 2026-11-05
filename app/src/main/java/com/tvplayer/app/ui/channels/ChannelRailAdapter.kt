package com.tvplayer.app.ui.channels

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tvplayer.app.R
import com.tvplayer.app.data.db.GroupNameCount
import com.tvplayer.app.ui.common.FocusScaleHelper

/** Rail (TV) / chips (móvil) de grupos. `group=null` es la fila "Todos". */
class ChannelRailAdapter(
    private val onSelect: (ChannelRailAdapter.Row) -> Unit,
) : RecyclerView.Adapter<ChannelRailAdapter.Holder>() {

    data class Row(val group: String?, val label: String, val count: Int)

    private var rows: List<Row> = emptyList()
    var selected: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /** "Todos" siemra primero + grupos con su conteo. */
    fun buildRows(groups: List<GroupNameCount>, allLabel: String): List<Row> {
        val total = groups.sumOf { it.count }
        return listOf(Row(null, allLabel, total)) + groups.map { Row(it.group, it.group, it.count) }
    }

    fun submit(list: List<Row>) {
        rows = list
        notifyDataSetChanged()
    }

    class Holder(root: View) : RecyclerView.ViewHolder(root) {
        val label: TextView = root.findViewById(R.id.group_label)
        val count: TextView = root.findViewById(R.id.group_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
        FocusScaleHelper.attach(v)
        return Holder(v)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        val active = selected == row.group
        holder.label.text = row.label
        holder.count.text = row.count.toString()
        holder.itemView.isActivated = active
        val ctx = holder.itemView.context
        holder.label.setTextColor(
            if (active) ctx.getColor(R.color.tp_text) else ctx.getColor(R.color.tp_text_secondary),
        )
        holder.itemView.setOnClickListener { onSelect(row) }
    }
}
