package com.tvplayer.app.ui.channels

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.TvNavigator
import com.tvplayer.app.core.xmltv.GuideWindow
import com.tvplayer.app.data.ActiveList
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.EpgProgramEntity
import com.tvplayer.app.ui.channel_dialog.ChannelSheetFragment
import com.tvplayer.app.ui.channels.ChannelSidebar.Selection
import com.tvplayer.app.ui.common.FocusScaleHelper
import com.tvplayer.app.ui.home.HomeFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Browser de canales fusionado con la guía (S3): sidebar (playlists + Todos
 * + Historial + grupos) que cambia la [ActiveList] global, hero del destacado
 * con "ahora" + progreso + siguiente, cabecera del timeline (3 h en cortes de
 * 30 min, [GuideWindow]) y filas con tira ahora/siguiente + timeline.
 * Room es el único estado: todo se lee de DB por ciclo.
 *
 * Dual layout: `layout/` (sidebar horizontal + columna) y `layout-television/`
 * (sidebar vertical 260dp + contenido, safe area 56dp). Foco TV vía
 * [FocusScaleHelper] 1.1. La grilla es una lista de 1 columna y JAMÁS carga
 * la lista completa en RAM (PagingData; Historial = snapshot cap 20).
 */
class ChannelBrowserFragment : Fragment() {

    companion object {
        const val TAG = "browser"
        const val ARG_LIST_ID = "listId"

        fun newInstance(listId: Long) = ChannelBrowserFragment().apply {
            arguments = bundleOf(ARG_LIST_ID to listId)
        }
    }

    private lateinit var viewModel: ChannelBrowserViewModel
    private var listId: Long = -1
    private var selection: Selection = Selection.All
    private lateinit var sidebarAdapter: ChannelSidebarAdapter
    private lateinit var gridAdapter: ChannelGridAdapter
    private var featured: ChannelEntity? = null
    private var window: GuideWindow = GuideWindow.now()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_channel_browser, container, false)
    }

    /** Rotación sin recrear (configChanges): reajusta columnas 1V/2H. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val grid = view?.findViewById<RecyclerView>(R.id.browser_grid) ?: return
        grid.layoutManager =
            GridLayoutManager(requireContext(), spanCount(FocusScaleHelper.isTv(grid), newConfig.orientation))
    }

    /** Una sola columna (ruling usuario: legibilidad): la grilla es una lista. */
    private fun spanCount(isTv: Boolean, orientation: Int): Int = 1

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val argId = arguments?.getLong(ARG_LIST_ID, -1L) ?: -1L
        val isTv = FocusScaleHelper.isTv(view)
        val container = (requireActivity().application as TpApp).container
        viewModel = ChannelBrowserViewModel(
            argId,
            container.db.channelDao(),
            container.db.epgDao(),
            container.db.recentDao(),
        )

        gridAdapter = ChannelGridAdapter(
            onClick = ::onChannelClick,
            onLongClick = { channel ->
                ChannelSheetFragment.newInstance(channel.listId, channel.stableKey)
                    .show(parentFragmentManager, ChannelSheetFragment.TAG)
            },
        )
        val grid = view.findViewById<RecyclerView>(R.id.browser_grid)
        grid.layoutManager = GridLayoutManager(requireContext(), spanCount(isTv, resources.configuration.orientation))
        grid.adapter = gridAdapter
        // Tira ahora/siguiente + timeline por pausa de scroll (doble batch).
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                fillTimeline()
            }
        })
        // Vacío en All/Group por estado de carga (Historial lo resuelve en
        // reloadContent: loadState no emite para PagingData.from en tests).
        gridAdapter.addLoadStateListener { states ->
            if (selection is Selection.History) return@addLoadStateListener
            val empty = view.findViewById<TextView>(R.id.browser_empty)
            empty.visibility =
                if (states.refresh is LoadState.NotLoading && gridAdapter.itemCount == 0) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }

        sidebarAdapter = ChannelSidebarAdapter(
            onPlaylist = { id ->
                lifecycleScope.launch {
                    ActiveList.set(container.db, id)
                    listId = id
                    viewModel.setListId(id)
                    selection = Selection.All
                    viewModel.select(Selection.All)
                    reloadAll()
                }
            },
            onSelection = { sel ->
                selection = sel
                viewModel.select(sel)
                sidebarAdapter.updateSelection(sel)
                reloadContent()
            },
        )
        val sidebar = view.findViewById<RecyclerView>(R.id.browser_sidebar)
        sidebar.layoutManager = LinearLayoutManager(
            requireContext(),
            if (isTv) LinearLayoutManager.VERTICAL else LinearLayoutManager.HORIZONTAL,
            false,
        )
        sidebar.adapter = sidebarAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // collectLatest: cada filtro nuevo cancela el submit anterior (si no,
                // el primer submitData se queda suspendido y la parrilla no cambia).
                viewModel.channels.collectLatest { pagingData -> gridAdapter.submitData(pagingData) }
            }
        }

        lifecycleScope.launch {
            val start = if (argId >= 0) argId else ActiveList.resolve(container.db)
            if (start == null) {
                (activity as? TvNavigator.NavHost)?.openTab(HomeFragment(), HomeFragment.TAG)
                return@launch
            }
            listId = start
            viewModel.setListId(start)
            reloadAll()
        }
    }

    private fun reloadAll() {
        lifecycleScope.launch {
            val view = view ?: return@launch
            if (listId < 0) return@launch
            val db = (requireActivity().application as TpApp).container.db
            val lists = db.listDao().all()
            val groups = viewModel.groups()
            val historyCount = db.recentDao().countForList(listId)
            sidebarAdapter.activeListId = listId
            sidebarAdapter.updateSelection(selection)
            sidebarAdapter.submit(
                ChannelSidebar.buildRows(
                    lists, listId, groups,
                    getString(R.string.browser_groups_all),
                    getString(R.string.browser_history),
                    getString(R.string.browser_no_group),
                    historyCount,
                ),
            )
            // Hero: destacado + ahora + progreso + siguiente.
            val hero = viewModel.hero()
            featured = hero?.channel
            val heroBox = view.findViewById<View>(R.id.browser_hero)
            if (hero != null) {
                heroBox.visibility = View.VISIBLE
                view.findViewById<TextView>(R.id.browser_hero_title).text = hero.channel.displayName
                val nowLine = when {
                    hero.nowTitle != null && hero.approximate ->
                        getString(R.string.player_now_approx, hero.nowTitle)
                    hero.nowTitle != null -> getString(R.string.player_now, hero.nowTitle)
                    else -> getString(R.string.player_now_placeholder)
                }
                val nextLine = hero.nextTitle?.let { getString(R.string.browser_next, it) }
                view.findViewById<TextView>(R.id.browser_hero_now).text =
                    listOfNotNull(nowLine, nextLine).joinToString(" · ")
                view.findViewById<ProgressBar>(R.id.browser_hero_progress).progress =
                    (hero.progress * 100).toInt().coerceIn(0, 100)
                val play = view.findViewById<Button>(R.id.browser_hero_play)
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
            window = GuideWindow.now()
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            view.findViewById<TextView>(R.id.browser_slots).text =
                window.slots().joinToString("  ·  ") { fmt.format(Date(it)) }
            reloadContent()
        }
    }

    private fun reloadContent() {
        lifecycleScope.launch {
            val view = view ?: return@launch
            if (listId < 0) return@launch
            sidebarAdapter.updateSelection(selection)
            val empty = view.findViewById<TextView>(R.id.browser_empty)
            // Historial: snapshot síncrono (el vacío se decide aquí, no por
            // loadState). All/Group lo decide el listener de carga.
            if (selection is Selection.History) {
                empty.visibility = if (viewModel.historyChannels().isEmpty()) View.VISIBLE else View.GONE
            }
            view.findViewById<RecyclerView>(R.id.browser_grid).scrollToPosition(0)
            // La tira EPG + timeline llegan tras el submit (visibles en idle).
            view.post { fillTimeline() }
        }
    }

    /**
     * Doble batch sobre los visibles → líneas "▶ ahora · después" (submitEpg)
     * + programas de la ventana por canal (submitTimeline). Clave: stableKey.
     */
    private fun fillTimeline() {
        val grid = view?.findViewById<RecyclerView>(R.id.browser_grid) ?: return
        val layout = grid.layoutManager as? GridLayoutManager ?: return
        val first = layout.findFirstVisibleItemPosition()
        val last = layout.findLastVisibleItemPosition()
        if (first < 0 || last < first) return
        lifecycleScope.launch {
            val db = (requireActivity().application as TpApp).container.db
            val ids = (first..last).mapNotNull { gridAdapter.peek(it)?.tvgId?.takeIf { t -> t.isNotBlank() } }
                .distinct()
            if (ids.isEmpty()) return@launch
            val now = System.currentTimeMillis()
            val nn = db.epgDao().nowNext(ids, now)
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
            val range = viewModel.timeline(ids, window)
            val perChannel = mutableMapOf<String, List<EpgProgramEntity>>()
            for (pos in first..last) {
                val ch = gridAdapter.peek(pos) ?: continue
                val tvgId = ch.tvgId?.takeIf { it.isNotBlank() } ?: continue
                range[tvgId]?.let { perChannel[ch.stableKey] = it }
            }
            if (perChannel.isNotEmpty()) gridAdapter.submitTimeline(perChannel, window)
        }
    }

    private fun onChannelClick(channel: ChannelEntity) {
        (activity as? TvNavigator.NavHost)?.openPlayer(channel.listId, channel.position)
    }
}
