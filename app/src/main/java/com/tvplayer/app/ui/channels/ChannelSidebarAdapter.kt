package com.tvplayer.app.ui.channels

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tvplayer.app.R
import com.tvplayer.app.ui.common.FocusScaleHelper

/**
 * Sidebar del browser (playlists + Todos + Historial + grupos) sobre
 * `item_group`: seleccionado = pastilla `state_activated` + etiqueta en
 * blanco; foco TV con escala 1.1 vía [FocusScaleHelper] + marquee.
 *
 * Fusión Guía→Canales (S1): renombre de GuideSidebarAdapter sin cambio de
 * comportamiento.
 */
class ChannelSidebarAdapter(
    private val onPlaylist: (Long) -> Unit,
    private val onSelection: (ChannelSidebar.Selection) -> Unit,
) : RecyclerView.Adapter<ChannelSidebarAdapter.Holder>() {

    private var rows: List<ChannelSidebar.Row> = emptyList()

    /** Selección de filtro actual (las playlists marcan por id activo). */
    var selectedFilter: ChannelSidebar.Selection = ChannelSidebar.Selection.All
    var activeListId: Long? = null

    fun submit(list: List<ChannelSidebar.Row>) {
        rows = list
        notifyDataSetChanged()
    }

    /** Marca el filtro y repinta (las playlists marcan por id activo). */
    fun updateSelection(filter: ChannelSidebar.Selection) {
        selectedFilter = filter
        notifyDataSetChanged()
    }

    class Holder(root: View) : RecyclerView.ViewHolder(root) {
        val label: TextView = root.findViewById(R.id.group_label)
        val count: TextView = root.findViewById(R.id.group_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
        return Holder(v)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        val ctx = holder.itemView.context
        val active = when (row) {
            is ChannelSidebar.Row.Playlist -> row.active
            is ChannelSidebar.Row.All -> selectedFilter is ChannelSidebar.Selection.All
            is ChannelSidebar.Row.History -> selectedFilter is ChannelSidebar.Selection.History
            is ChannelSidebar.Row.Group ->
                (selectedFilter as? ChannelSidebar.Selection.Group)?.name == row.name
        }
        holder.label.text = when (row) {
            is ChannelSidebar.Row.Playlist -> row.name.ifBlank { ctx.getString(R.string.lists_name) }
            is ChannelSidebar.Row.All -> row.label
            is ChannelSidebar.Row.History -> row.label
            is ChannelSidebar.Row.Group -> row.label
        }
        val countText = when (row) {
            is ChannelSidebar.Row.Playlist -> ""
            is ChannelSidebar.Row.All -> row.count.toString()
            is ChannelSidebar.Row.History -> row.count.toString()
            is ChannelSidebar.Row.Group -> row.count.toString()
        }
        holder.count.text = countText
        holder.count.visibility = if (countText.isBlank()) View.GONE else View.VISIBLE
        holder.itemView.isActivated = active
        holder.label.setTextColor(
            if (active) ctx.getColor(R.color.tp_text) else ctx.getColor(R.color.tp_text_secondary),
        )
        holder.itemView.setOnClickListener {
            when (row) {
                is ChannelSidebar.Row.Playlist -> onPlaylist(row.id)
                is ChannelSidebar.Row.All -> onSelection(ChannelSidebar.Selection.All)
                is ChannelSidebar.Row.History -> onSelection(ChannelSidebar.Selection.History)
                is ChannelSidebar.Row.Group -> onSelection(ChannelSidebar.Selection.Group(row.name))
            }
        }
        FocusScaleHelper.attach(holder.itemView) { _, focused ->
            holder.label.isSelected = focused
        }
    }
}
