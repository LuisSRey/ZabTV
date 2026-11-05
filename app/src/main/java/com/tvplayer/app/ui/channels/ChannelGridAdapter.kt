package com.tvplayer.app.ui.channels

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.tvplayer.app.R
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.ui.common.FocusScaleHelper

/**
 * Grilla de canales paginada (never full-RAM). Logos vía Coil — el singleton
 * global de Coil lo instala TpApp sobre el OkHttp compartido, así la carga de
 * imágenes pasa por el mismo únicos CleartextGate que todo el transporte.
 *
 * Tira "Ahora/Siguiente" (Task 14): el fragment la alimenta por lote con
 * EpgDao.nowNext sobre los visibles; la clave es stableKey.
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
    }

    private var epgLines: Map<String, String> = emptyMap()

    /** Actualiza la tira EPG sin recargar la grilla (solo rebind de visibles). */
    fun submitEpg(lines: Map<String, String>) {
        epgLines = lines
        notifyItemRangeChanged(0, itemCount, PAYLOAD_EPG)
    }

    class Holder(root: View) : RecyclerView.ViewHolder(root) {
        val logo: ImageView = root.findViewById(R.id.channel_logo)
        val name: TextView = root.findViewById(R.id.channel_name)
        val epg: TextView? = root.findViewById(R.id.channel_epg)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        FocusScaleHelper.attach(v)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        onBind(holder, position, full = true)
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_EPG)) {
            onBind(holder, position, full = false)
        } else {
            onBind(holder, position, full = true)
        }
    }

    private fun onBind(holder: Holder, position: Int, full: Boolean) {
        val channel = getItem(position) ?: return
        if (full) {
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
        val line = epgLines[channel.stableKey]
        holder.epg?.let {
            it.text = line.orEmpty()
            it.visibility = if (line.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }
}

private const val PAYLOAD_EPG = "epg"
