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
 */
class ChannelGridAdapter(
    private val onClick: (ChannelEntity) -> Unit,
) : PagingDataAdapter<ChannelEntity, ChannelGridAdapter.Holder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChannelEntity>() {
            override fun areItemsTheSame(oldItem: ChannelEntity, newItem: ChannelEntity) =
                oldItem.stableKey == newItem.stableKey

            override fun areContentsTheSame(oldItem: ChannelEntity, newItem: ChannelEntity) =
                oldItem == newItem
        }
    }

    class Holder(root: View) : RecyclerView.ViewHolder(root) {
        val logo: ImageView = root.findViewById(R.id.channel_logo)
        val name: TextView = root.findViewById(R.id.channel_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        FocusScaleHelper.attach(v)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val channel = getItem(position) ?: return
        holder.name.text = channel.displayName
        holder.logo.setImageDrawable(null) // reciclado: sin logo fantasma
        channel.logoUrl?.let { holder.logo.load(it) }
        holder.itemView.setOnClickListener { onClick(channel) }
    }
}
