package com.tvplayer.app.ui.channels

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.tvplayer.app.R
import com.tvplayer.app.core.xmltv.GuideWindow
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.EpgProgramEntity
import com.tvplayer.app.ui.common.FocusScaleHelper

/**
 * Grilla de canales paginada (never full-RAM). Logos vía Coil — el singleton
 * global de Coil lo instala TpApp sobre el OkHttp compartido, así la carga de
 * imágenes pasa por el mismo únicos CleartextGate que todo el transporte.
 *
 * Tira "Ahora/Siguiente" (Task 14): el fragment la alimenta por lote con
 * EpgDao.nowNext sobre los visibles; la clave es stableKey.
 *
 * Timeline (fusión Guía→Canales S2): cada fila lleva nº + logo + nombre + 6
 * celdas ([GuideWindow]); el fragment lo alimenta por lote con
 * EpgDao.programsInRange sobre los visibles. Ambos lotes actualizan por
 * payloads parciales (sin recargar la grilla); [peek] intacto.
 */
class ChannelGridAdapter(
    private val onClick: (ChannelEntity) -> Unit,
    private val onLongClick: ((ChannelEntity) -> Unit)? = null,
) : PagingDataAdapter<ChannelEntity, ChannelGridAdapter.Holder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChannelEntity>() {
            override fun areItemsTheSame(oldItem: ChannelEntity, newItem: ChannelEntity) =
                oldItem.stableKey == newItem.stableKey

            override fun areContentsTheSame(oldItem: ChannelEntity, newItem: ChannelEntity) =
                oldItem == newItem
        }

        /**
         * Un título por corte: el programa en el aire al inicio del corte;
         * "" sin guía en ese corte. Puro para test directo.
         */
        fun cellTitles(programs: List<EpgProgramEntity>, window: GuideWindow): List<String> {
            val slots = window.slots()
            val ends = slots.map { it + window.slotMinutes * 60_000L }
            return slots.indices.map { i ->
                programs.firstOrNull { it.startMillis <= slots[i] && it.endMillis > slots[i] }?.title
                    ?: programs.firstOrNull { it.startMillis >= slots[i] && it.startMillis < ends[i] }?.title
                    ?: ""
            }
        }
    }

    private var epgLines: Map<String, String> = emptyMap()
    private var timeline: Map<String, List<EpgProgramEntity>> = emptyMap()
    private var window: GuideWindow? = null

    /** Actualiza la tira EPG sin recargar la grilla (solo rebind de visibles). */
    fun submitEpg(lines: Map<String, String>) {
        epgLines = lines
        notifyItemRangeChanged(0, itemCount, PAYLOAD_EPG)
    }

    /** Fija la ventana del timeline (p. ej. al rellenar antes del lote). */
    fun setWindow(window: GuideWindow) {
        this.window = window
        notifyItemRangeChanged(0, itemCount, PAYLOAD_TIMELINE)
    }

    /** Actualiza el timeline sin recargar la grilla (solo rebind de visibles). */
    fun submitTimeline(lines: Map<String, List<EpgProgramEntity>>, window: GuideWindow) {
        timeline = lines
        setWindow(window)
    }

    class Holder(root: View) : RecyclerView.ViewHolder(root) {
        // Nulables null-safe: el layout TV no trae number ni timeline.
        val number: TextView? = root.findViewById(R.id.channel_number)
        val logo: ImageView = root.findViewById(R.id.channel_logo)
        val name: TextView = root.findViewById(R.id.channel_name)
        val epg: TextView? = root.findViewById(R.id.channel_epg)
        val timeline: LinearLayout? = root.findViewById(R.id.channel_timeline)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        FocusScaleHelper.attach(v)
        val holder = Holder(v)
        // Solo el layout con tira lleva celdas (TV sin timeline = 0 celdas).
        if (holder.timeline != null) {
            val inflater = LayoutInflater.from(parent.context)
            val slots = GuideWindow.DEFAULT_SLOT_COUNT
            repeat(slots) {
                inflater.inflate(R.layout.item_channel_timeline, holder.timeline, true)
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        onBind(holder, position, full = true)
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBind(holder, position, full = true)
            return
        }
        if (payloads.contains(PAYLOAD_EPG)) bindEpg(holder, position)
        if (payloads.contains(PAYLOAD_TIMELINE)) bindTimeline(holder, position)
        if (!payloads.contains(PAYLOAD_EPG) && !payloads.contains(PAYLOAD_TIMELINE)) {
            onBind(holder, position, full = true)
        }
    }

    private fun onBind(holder: Holder, position: Int, full: Boolean) {
        val channel = getItem(position) ?: return
        if (full) {
            holder.number?.text = (channel.position + 1).toString()
            holder.name.text = channel.displayName
            holder.logo.setImageDrawable(null) // reciclado: sin logo fantasma
            val radiusPx = 8f * holder.itemView.resources.displayMetrics.density
            channel.logoUrl?.let { holder.logo.load(it) {
                transformations(coil.transform.RoundedCornersTransformation(radiusPx))
            } }
            holder.itemView.setOnClickListener { onClick(channel) }
            holder.itemView.setOnLongClickListener {
                onLongClick?.invoke(channel)
                onLongClick != null
            }
        }
        bindEpg(holder, position)
        bindTimeline(holder, position)
    }

    private fun bindEpg(holder: Holder, position: Int) {
        val channel = getItem(position) ?: return
        val line = epgLines[channel.stableKey]
        holder.epg?.let {
            it.text = line.orEmpty()
            it.visibility = if (line.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun bindTimeline(holder: Holder, position: Int) {
        // Sin tira (TV) no hay nada que pintar: se sale sin tocar nada.
        val strip = holder.timeline ?: return
        val w = window
        // Sin ventana (rail de Favoritos/Buscar en Home) no hay timeline.
        if (w == null) {
            strip.visibility = View.GONE
            return
        }
        // Con ventana la tira va SIEMPRE visible; el hueco sin guía usa el
        // string existente de vacío (browser_empty), nunca "—".
        strip.visibility = View.VISIBLE
        val channel = getItem(position) ?: return
        val emptyLabel = holder.itemView.context.getString(R.string.browser_empty)
        val titles = cellTitles(timeline[channel.stableKey].orEmpty(), w)
        for (i in 0 until strip.childCount) {
            (strip.getChildAt(i) as? TextView)?.text =
                titles.getOrElse(i) { "" }.ifBlank { emptyLabel }
        }
    }
}

private const val PAYLOAD_EPG = "epg"
private const val PAYLOAD_TIMELINE = "timeline"
