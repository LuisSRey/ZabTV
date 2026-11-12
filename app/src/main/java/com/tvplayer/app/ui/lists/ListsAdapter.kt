package com.tvplayer.app.ui.lists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tvplayer.app.R
import com.tvplayer.app.ui.common.FocusScaleHelper

/**
 * Fila de lista: nombre + estado • nº canales; abrir / actualizar / borrar /
 * activar / editar / EPG.
 *
 * TV: la raíz NO es focable — el foco entra por los botones (si la raíz lo
 * tomara, Actualizar/Borrar quedarían inalcanzables con el D-pad); el tap de
 * fila sigue abriendo la lista en móvil (listener cableado en bind).
 */
class ListsAdapter(
    private val onOpen: (Row) -> Unit,
    private val onRefresh: (Row) -> Unit,
    private val onDelete: (Row) -> Unit,
    private val onEdit: (Row) -> Unit,
    private val onEpg: (Row) -> Unit,
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
        val open: Button = root.findViewById(R.id.list_open)
        val refresh: Button = root.findViewById(R.id.list_refresh)
        val delete: Button = root.findViewById(R.id.list_delete)
        val activate: Button = root.findViewById(R.id.list_activate)
        val edit: Button = root.findViewById(R.id.list_edit)
        val epg: Button = root.findViewById(R.id.list_epg)

        init {
            // Foco+escala por botón (TV); cada attach pasea los ancestros y
            // abre su clip para que el halo 1.1 no se recorte. La raíz queda
            // fuera de la cadena de foco.
            listOf(open, refresh, delete, activate, edit, epg).forEach {
                FocusScaleHelper.attach(it)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_list, parent, false)
        // Sin attach en la raíz: en TV el D-pad entra por los botones.
        return Holder(v)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        holder.name.text = row.name
        holder.subtitle.text = row.subtitle
        holder.itemView.setOnClickListener { onOpen(row) }
        holder.open.setOnClickListener { onOpen(row) }
        holder.refresh.setOnClickListener { onRefresh(row) }
        holder.delete.setOnClickListener { onDelete(row) }
        holder.activate.setOnClickListener { onActivate(row) }
        holder.activate.isEnabled = !row.isActive
        holder.edit.setOnClickListener { onEdit(row) }
        holder.epg.setOnClickListener { onEpg(row) }
    }
}
