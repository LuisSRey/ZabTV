package com.tvplayer.app.ui.guide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.TvNavigator
import com.tvplayer.app.core.xmltv.GuideWindow
import com.tvplayer.app.data.ActiveList
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.ui.channel_dialog.ChannelSheetFragment
import com.tvplayer.app.ui.channels.ChannelGridAdapter
import com.tvplayer.app.ui.channels.ChannelSidebar
import com.tvplayer.app.ui.channels.ChannelSidebarAdapter
import com.tvplayer.app.ui.common.FocusScaleHelper
import com.tvplayer.app.ui.home.HomeFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Guía de canales: sidebar (playlists + Todos + Historial + grupos) que cambia
 * la [ActiveList] global, hero del destacado con "ahora" + progreso, cabecera
 * del timeline (3 h en cortes de 30 min, [GuideWindow]) y filas con tira
 * ahora/siguiente. Room es el único estado: todo se lee de DB por ciclo.
 *
 * Dual layout: `layout/` (sidebar horizontal + columna) y `layout-television/`
 * (sidebar vertical 260dp + contenido). Foco TV vía [FocusScaleHelper] 1.1.
 */
class GuideFragment : Fragment() {

    companion object {
        const val TAG = "guide"
    }

    private var listId: Long = -1
    private var selection: ChannelSidebar.Selection = ChannelSidebar.Selection.All
    private lateinit var sidebarAdapter: ChannelSidebarAdapter
    private lateinit var gridAdapter: ChannelGridAdapter
    private var featured: ChannelEntity? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_guide, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val isTv = FocusScaleHelper.isTv(view)
        val db = (requireActivity().application as TpApp).container.db

        gridAdapter = ChannelGridAdapter(
            onClick = { channel ->
                (activity as? TvNavigator.NavHost)?.openPlayer(channel.listId, channel.position)
            },
            onLongClick = { channel ->
                ChannelSheetFragment.newInstance(channel.listId, channel.stableKey)
                    .show(parentFragmentManager, ChannelSheetFragment.TAG)
            },
        )
        val grid = view.findViewById<RecyclerView>(R.id.guide_grid)
        grid.layoutManager = GridLayoutManager(requireContext(), 1)
        grid.adapter = gridAdapter
        // Tira ahora/siguiente por pausa de scroll (mismo batch que el browser).
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                fillEpgLines()
            }
        })

        sidebarAdapter = ChannelSidebarAdapter(
            onPlaylist = { id ->
                lifecycleScope.launch {
                    ActiveList.set(db, id)
                    listId = id
                    selection = ChannelSidebar.Selection.All
                    reloadAll()
                }
            },
            onSelection = { sel ->
                selection = sel
                sidebarAdapter.updateSelection(sel)
                reloadContent()
            },
        )
        val sidebar = view.findViewById<RecyclerView>(R.id.guide_sidebar)
        sidebar.layoutManager = LinearLayoutManager(
            requireContext(),
            if (isTv) LinearLayoutManager.VERTICAL else LinearLayoutManager.HORIZONTAL,
            false,
        )
        sidebar.adapter = sidebarAdapter

        lifecycleScope.launch {
            val active = ActiveList.resolve(db)
            if (active == null) {
                (activity as? TvNavigator.NavHost)?.openTab(HomeFragment(), HomeFragment.TAG)
                return@launch
            }
            listId = active
            reloadAll()
        }
    }

    private fun reloadAll() {
        lifecycleScope.launch {
            val view = view ?: return@launch
            val db = (requireActivity().application as TpApp).container.db
            val lists = db.listDao().all()
            val vm = GuideViewModel(listId, db.channelDao(), db.epgDao(), db.recentDao())
            val groups = vm.groups()
            val historyCount = db.recentDao().countForList(listId)
            sidebarAdapter.activeListId = listId
            sidebarAdapter.updateSelection(selection)
            sidebarAdapter.submit(
                ChannelSidebar.buildRows(
                    lists, listId, groups,
                    getString(R.string.browser_groups_all),
                    getString(R.string.guide_history),
                    getString(R.string.browser_no_group),
                    historyCount,
                ),
            )
            // Hero: destacado + ahora + progreso + siguiente.
            val hero = vm.hero()
            featured = hero?.channel
            val heroBox = view.findViewById<View>(R.id.guide_hero)
            if (hero != null) {
                heroBox.visibility = View.VISIBLE
                view.findViewById<TextView>(R.id.guide_hero_title).text = hero.channel.displayName
                val nowLine = when {
                    hero.nowTitle != null && hero.approximate ->
                        getString(R.string.player_now_approx, hero.nowTitle)
                    hero.nowTitle != null -> getString(R.string.player_now, hero.nowTitle)
                    else -> getString(R.string.player_now_placeholder)
                }
                val nextLine = hero.nextTitle?.let { getString(R.string.guide_next, it) }
                view.findViewById<TextView>(R.id.guide_hero_now).text =
                    listOfNotNull(nowLine, nextLine).joinToString(" · ")
                view.findViewById<ProgressBar>(R.id.guide_hero_progress).progress =
                    (hero.progress * 100).toInt().coerceIn(0, 100)
                val play = view.findViewById<Button>(R.id.guide_hero_play)
                FocusScaleHelper.attach(play)
                play.setOnClickListener {
                    val ch = featured
                    if (ch != null) {
                        (activity as? TvNavigator.NavHost)?.openPlayer(ch.listId, ch.position)
                    }
                }
            } else {
                heroBox.visibility = View.GONE
            }
            // Cabecera del timeline: 6 cortes de 30 min desde el anchor.
            val window = GuideWindow.now()
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            view.findViewById<TextView>(R.id.guide_slots).text =
                window.slots().joinToString("  ·  ") { fmt.format(Date(it)) }
            reloadContent()
        }
    }

    private fun reloadContent() {
        lifecycleScope.launch {
            val view = view ?: return@launch
            if (listId < 0) return@launch
            val db = (requireActivity().application as TpApp).container.db
            sidebarAdapter.updateSelection(selection)
            val vm = GuideViewModel(listId, db.channelDao(), db.epgDao(), db.recentDao())
            val channels = vm.channelsForFilter(selection)
            val empty = view.findViewById<TextView>(R.id.guide_empty)
            empty.visibility = if (channels.isEmpty()) View.VISIBLE else View.GONE
            gridAdapter.submitData(PagingData.from(channels))
            // La tira EPG llega tras el submit (visibles conocidos en idle).
            view.findViewById<RecyclerView>(R.id.guide_grid).scrollToPosition(0)
            view.post { fillEpgLines() }
        }
    }

    /** Batch nowNext sobre los visibles → líneas "▶ ahora · después". */
    private fun fillEpgLines() {
        val grid = view?.findViewById<RecyclerView>(R.id.guide_grid) ?: return
        val layout = grid.layoutManager as? GridLayoutManager ?: return
        val first = layout.findFirstVisibleItemPosition()
        val last = layout.findLastVisibleItemPosition()
        if (first < 0 || last < first) return
        lifecycleScope.launch {
            val db = (requireActivity().application as TpApp).container.db
            val ids = (first..last).mapNotNull { gridAdapter.peek(it)?.tvgId?.takeIf { t -> t.isNotBlank() } }
                .distinct()
            if (ids.isEmpty()) return@launch
            val nn = db.epgDao().nowNext(ids, System.currentTimeMillis())
            val lines = mutableMapOf<String, String>()
            for (pos in first..last) {
                val ch = gridAdapter.peek(pos) ?: continue
                val tvgId = ch.tvgId?.takeIf { it.isNotBlank() } ?: continue
                val (nowP, nextP) = nn[tvgId] ?: continue
                val line = listOfNotNull(
                    nowP?.title?.let { "▶ $it" },
                    nextP?.title?.let { "· $it" },
                ).joinToString(" ").takeIf { it.isNotBlank() } ?: continue
                lines[ch.stableKey] = line
            }
            if (lines.isNotEmpty()) gridAdapter.submitEpg(lines)
        }
    }
}
