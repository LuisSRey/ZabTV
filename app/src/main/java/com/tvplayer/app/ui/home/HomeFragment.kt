package com.tvplayer.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.TvNavigator
import com.tvplayer.app.data.ActiveList
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.repo.EpgMatcher
import com.tvplayer.app.data.repo.FavouritesRepo
import com.tvplayer.app.ui.channels.ChannelGridAdapter
import com.tvplayer.app.ui.common.FocusScaleHelper
import kotlinx.coroutines.launch

/**
 * Home estilo app tvOS (spec §5): hero panorámico del destacado + estantería
 * de Favoritos. Sin listas muestra el disclaimer de "no hay contenido incluido".
 * La navegación vive en el shell (sidebar TV / barra móvil); aquí solo contenido.
 */
class HomeFragment : Fragment() {

    companion object {
        const val TAG = "home"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val hero = view.findViewById<View>(R.id.home_hero)
        val heroLogo = view.findViewById<ImageView>(R.id.hero_logo)
        val heroTitle = view.findViewById<TextView>(R.id.hero_title)
        val heroNow = view.findViewById<TextView>(R.id.hero_now)
        val heroPlay = view.findViewById<Button>(R.id.hero_play)
        val disclaimer = view.findViewById<TextView>(R.id.home_disclaimer)
        val favsSection = view.findViewById<LinearLayout>(R.id.home_favs_section)
        val favsRail = view.findViewById<RecyclerView>(R.id.home_favs_rail)
        FocusScaleHelper.attach(heroPlay)

        val favsAdapter = ChannelGridAdapter(onClick = { channel ->
            (activity as? TvNavigator.NavHost)?.openPlayer(channel.listId, channel.position)
        })
        favsRail.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        favsRail.adapter = favsAdapter

        lifecycleScope.launch {
            val db = (requireActivity().application as TpApp).container.db
            val activeId = ActiveList.resolve(db)
            if (activeId == null) {
                disclaimer.visibility = View.VISIBLE
                return@launch
            }
            // Destacado: último reproducido; si no hay, primer canal de la lista activa.
            val featured: ChannelEntity? =
                com.tvplayer.app.data.LastPlayed.resolve(db)
                    ?: db.channelDao().channelAtPosition(activeId, 0)
            if (featured != null) {
                hero.visibility = View.VISIBLE
                heroTitle.text = featured.displayName
                val hr = 12f * view.resources.displayMetrics.density
                featured.logoUrl?.let { heroLogo.load(it) {
                    transformations(coil.transform.RoundedCornersTransformation(hr))
                } }
                val match = EpgMatcher(db.epgDao()).match(featured)
                val nn = if (match != null) {
                    db.epgDao().nowNext(listOf(match.tvgId), System.currentTimeMillis())
                } else {
                    emptyMap()
                }
                val nowTitle = match?.let { nn[it.tvgId]?.first?.title }
                heroNow.text = if (nowTitle != null) {
                    getString(R.string.player_now, nowTitle)
                } else {
                    getString(R.string.player_now_placeholder)
                }
                heroPlay.setOnClickListener {
                    (activity as? TvNavigator.NavHost)?.openPlayer(featured.listId, featured.position)
                }
            }
            val favs = FavouritesRepo(db).favourites(activeId)
            if (favs.isNotEmpty()) {
                favsSection.visibility = View.VISIBLE
                favsAdapter.submitData(PagingData.from(favs))
            }
            // Estantería Mis listas: rail horizontal, tarjetas adyacentes.
            val listsSection = view.findViewById<LinearLayout>(R.id.home_lists_section)
            val listsRail = view.findViewById<RecyclerView>(R.id.home_lists_rail)
            val lists = db.listDao().all()
            if (lists.isNotEmpty()) {
                listsSection.visibility = View.VISIBLE
                listsRail.layoutManager =
                    LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                listsRail.adapter = ListsShelfAdapter(
                    lists.map { entity ->
                        ListsShelfAdapter.Row(entity.id, entity.name)
                    },
                ) { row ->
                    (activity as? TvNavigator.NavHost)?.openBrowser(row.id)
                }
            }
        }
    }

    /** Tarjetas compactas de lista para la estantería de Home. */
    private class ListsShelfAdapter(
        private val rows: List<Row>,
        private val onOpen: (Row) -> Unit,
    ) : RecyclerView.Adapter<ListsShelfAdapter.Holder>() {

        data class Row(val id: Long, val name: String)

        class Holder(root: View) : RecyclerView.ViewHolder(root) {
            val label: TextView = root.findViewById(R.id.group_label)
            val count: TextView = root.findViewById(R.id.group_count)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
            FocusScaleHelper.attach(v)
            v.findViewById<View>(R.id.group_count).visibility = View.GONE
            return Holder(v)
        }

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            holder.label.text = row.name
            holder.itemView.setOnClickListener { onOpen(row) }
        }
    }
}

class AboutSpotDialog : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val text = TextView(requireContext())
        text.text = getString(com.tvplayer.app.R.string.about_disclaimer_full)
        text.setPadding(48, 48, 48, 48)
        text.setTextSize(16f)
        return text
    }
}
