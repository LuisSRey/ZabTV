package com.tvplayer.app.ui.channels

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.tvplayer.app.R
import com.tvplayer.app.core.UrlPolicer
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

    /**
     * Item total: fuera de rango o placeholder → null (limpia, nunca rompe).
     * El getItem() de Paging lanza IndexOutOfBounds fuera de rango en vez de
     * devolver null (PageStore.checkIndex): sin este guardián, un bind con
     * posición rancia (p. ej. durante animaciones) rompería antes de limpiar.
     */
    private fun safeItem(position: Int): ChannelEntity? =
        if (position in 0 until itemCount) getItem(position) else null

    /** Ventana vigente (CA2: prueba que el rotate re-dispara setWindow). */
    @VisibleForTesting
    internal val currentWindow: GuideWindow?
        get() = window

    /** Actualiza la tira EPG sin recargar la grilla (solo rebind de visibles). Clave: stableKey. */
    fun submitEpg(lines: Map<String, String>) {
        epgLines = lines
        notifyItemRangeChanged(0, itemCount, PAYLOAD_EPG)
    }

    /** Fija la ventana del timeline (p. ej. al rellenar antes del lote). */
    fun setWindow(window: GuideWindow) {
        this.window = window
        notifyItemRangeChanged(0, itemCount, PAYLOAD_TIMELINE)
    }

    /** Actualiza el timeline sin recargar la grilla (solo rebind de visibles). Clave: stableKey. */
    fun submitTimeline(lines: Map<String, List<EpgProgramEntity>>, window: GuideWindow) {
        timeline = lines
        setWindow(window)
    }

    class Holder(root: View) : RecyclerView.ViewHolder(root) {
        // Nulables null-safe: el layout móvil no trae inner (tile TV).
        val inner: View? = root.findViewById(R.id.channel_tile_inner)
        val number: TextView? = root.findViewById(R.id.channel_number)
        val logo: ImageView = root.findViewById(R.id.channel_logo)
        val name: TextView = root.findViewById(R.id.channel_name)
        val epg: TextView? = root.findViewById(R.id.channel_epg)
        val timeline: LinearLayout? = root.findViewById(R.id.channel_timeline)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        val holder = Holder(v)
        // B1: foco y escala en la raíz (attach clásico unificado TV/móvil);
        // el inner TV duplica el estado de la raíz para pintar el borde de
        // foco, sin animación propia.
        FocusScaleHelper.attach(v)
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
        val channel = safeItem(position)
        if (full && channel != null) {
            holder.number?.text = (channel.position + 1).toString()
            holder.name.text = channel.displayName
            bindLogo(holder.logo, channel.logoUrl)
            holder.itemView.setOnClickListener { onClick(channel) }
            holder.itemView.setOnLongClickListener {
                onLongClick?.invoke(channel)
                onLongClick != null
            }
        }
        bindEpg(holder, position)
        bindTimeline(holder, position)
    }

    /**
     * Logo con tag-check (U2 S4): la misma URL no recarga (evita parpadeo en
     * rebinds por payload); sin URL o prohibida por [UrlPolicer] va el
     * placeholder sin tocar red ni decoder. Carga 96×56 sin crossfade,
     * esquinas 8dp, placeholder+error del escudo TV.
     */
    @VisibleForTesting
    internal fun bindLogo(logo: ImageView, url: String?) {
        val clean = url?.trim().takeIf { UrlPolicer.isAllowedHttpUrl(it) }
        if (clean == null) {
            logo.tag = null
            logo.setImageResource(R.drawable.tile_logo_placeholder)
            return
        }
        if (logo.tag == clean) {
            BrowserPerfCounters.incCoilSkip()
            return
        }
        logo.tag = clean
        BrowserPerfCounters.incCoilLoad()
        val radiusPx = 8f * logo.resources.displayMetrics.density
        logo.load(clean) {
            crossfade(false)
            placeholder(R.drawable.tile_logo_placeholder)
            error(R.drawable.tile_logo_placeholder)
            size(96, 56)
            transformations(RoundedCornersTransformation(radiusPx))
        }
    }

    /** Reciclado: raíz sin escala fantasma + logo sin fantasma ni tag rancio (el próximo bind decide). */
    override fun onViewRecycled(holder: Holder) {
        holder.itemView.animate().cancel()
        holder.itemView.scaleX = 1.0f
        holder.itemView.scaleY = 1.0f
        holder.itemView.translationZ = 0f
        // Legado A1/A2: el inner ya no anima (B1), pero se deja en 1.0 por
        // seguridad ante holders reciclados de la geometría anterior.
        holder.inner?.let {
            it.animate().cancel()
            it.scaleX = 1.0f
            it.scaleY = 1.0f
            it.translationZ = 0f
        }
        holder.logo.tag = null
        holder.logo.setImageDrawable(null)
        super.onViewRecycled(holder)
    }

    private fun bindEpg(holder: Holder, position: Int) {
        // Sin item no hay clave: la tira se limpia (nada rancio del reciclado).
        val line = safeItem(position)?.let { epgLines[it.stableKey] }
        holder.epg?.let {
            it.text = line.orEmpty()
            it.visibility = if (line.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun bindTimeline(holder: Holder, position: Int) {
        // Sin tira (layouts sin timeline) no hay nada que pintar: TV intacto.
        val strip = holder.timeline ?: return
        val emptyLabel = holder.itemView.context.getString(R.string.browser_empty)
        val w = window
        val channel = safeItem(position)
        // Sin ventana (rail de Favoritos/Buscar en Home) la tira se oculta;
        // sin item no hay clave. Ambas ramas limpian las celdas a vacío para
        // no mostrar títulos rancios del reciclado. Con ventana la tira va
        // SIEMPRE visible con 6 celdas: títulos o la etiqueta de vacío
        // ([TimelineCellMapper], nunca el nombre del canal). La clave es
        // stableKey. La tira channel_epg va separada (binds aislados).
        strip.visibility = if (w == null) View.GONE else View.VISIBLE
        val titles = if (w != null && channel != null) {
            TimelineCellMapper.mapCells(timeline[channel.stableKey].orEmpty(), w, emptyLabel)
        } else {
            emptyList()
        }
        for (i in 0 until strip.childCount) {
            val cell = strip.getChildAt(i) as? TextView ?: continue
            val text = titles.getOrElse(i) { emptyLabel }
            cell.text = text
            cell.contentDescription = text
        }
    }
}

private const val PAYLOAD_EPG = "epg"
private const val PAYLOAD_TIMELINE = "timeline"
