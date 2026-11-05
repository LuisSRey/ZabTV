package com.tvplayer.app.ui.lists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tvplayer.app.R
import com.tvplayer.app.ui.common.FocusScaleHelper

/** Fila de lista: nombre + estado • nº canales; abrir / actualizar / borrar / activar. */
class ListsAdapter(
    private val onOpen: (Row) -> Unit,
    private val onRefresh: (Row) -> Unit,
    private val onDelete: (Row) -> Unit,
    private val onActivate: (Row) -> Unit,
) : RecyclerView.Adapter<ListsAdapter.Holder>() {

    data class Row(val id: Long, val name: String, val subtitle: String, val isActive: Boolean)

    private var rows: List<Row> = emptyList()

    fun submit(list: List<Row>) {
        rows = list
        notifyDataSetChanged()
    }

    class Holder(root: View) : RecyclerView.ViewHolder(root) {
        val name: TextView = root.findViewById(R.id.list_name)
        val subtitle: TextView = root.findViewById(R.id.list_subtitle)
        val refresh: Button = root.findViewById(R.id.list_refresh)
        val delete: Button = root.findViewById(R.id.list_delete)
        val activate: Button = root.findViewById(R.id.list_activate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_list, parent, false)
        FocusScaleHelper.attach(v)
        return Holder(v)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        holder.name.text = row.name
        holder.subtitle.text = row.subtitle
        holder.itemView.setOnClickListener { onOpen(row) }
        holder.refresh.setOnClickListener { onRefresh(row) }
        holder.delete.setOnClickListener { onDelete(row) }
        holder.activate.setOnClickListener { onActivate(row) }
        holder.activate.isEnabled = !row.isActive
    }
}
