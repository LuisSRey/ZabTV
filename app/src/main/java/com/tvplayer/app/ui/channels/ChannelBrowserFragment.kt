package com.tvplayer.app.ui.channels

import android.app.AlertDialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupMenu
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
import com.tvplayer.app.data.db.ListEntity
import com.tvplayer.app.ui.channel_dialog.ChannelSheetFragment
import com.tvplayer.app.ui.channels.ChannelSidebar.Selection
import com.tvplayer.app.ui.common.FocusScaleHelper
import com.tvplayer.app.ui.home.HomeFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Browser de canales fusionado con la guía (S3): sidebar (playlists + Todos
 * + Historial + grupos) que cambia la [ActiveList] global (en móvil las
 * playlists viven en el dropdown `browser_list_selector` y el rail queda
 * solo para filtros), hero del destacado
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

        /** Reintento acotado del timeline: tope de reintentos tras grid vacía. */
        const val TIMELINE_MAX_RETRY = 2

        /** Espera entre reintentos del timeline. */
        const val TIMELINE_RETRY_DELAY_MS = 200L

        /**
         * Cabecera del timeline: 6 cortes "HH:mm" de la misma [window] que
         * las filas (S6). Puro para test directo.
         */
        fun slotsText(
            window: GuideWindow,
            locale: java.util.Locale = java.util.Locale.getDefault(),
        ): String {
            val fmt = java.text.SimpleDateFormat("HH:mm", locale)
            return window.slots().joinToString("  ·  ") { fmt.format(java.util.Date(it)) }
        }

        /**
         * Etiquetas del diálogo de playlists (TV): nombre de cada lista,
         * con fallback a `lists_name` si viene en blanco. Puro para test
         * directo.
         */
        fun listDialogLabels(lists: List<ListEntity>, fallback: String): Array<String> =
            lists.map { it.name.ifBlank { fallback } }.toTypedArray()

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
        // reloadContent: loadState no emite para PagingData.from en tests). Con
        // datos (NotLoading + items) se pinta el timeline tras el submit.
        gridAdapter.addLoadStateListener { states ->
            if (selection is Selection.History) return@addLoadStateListener
            if (states.refresh !is LoadState.NotLoading) return@addLoadStateListener
            val empty = view.findViewById<TextView>(R.id.browser_empty)
            if (gridAdapter.itemCount == 0) {
                empty.visibility = View.VISIBLE
            } else {
                empty.visibility = View.GONE
                grid.post { fillTimeline() }
            }
        }

        sidebarAdapter = ChannelSidebarAdapter(
            onPlaylist = ::onListPicked,
            onSelection = ::onFilterPicked,
        )
        val sidebar = view.findViewById<RecyclerView>(R.id.browser_sidebar)
        sidebar.layoutManager = LinearLayoutManager(
            requireContext(),
            if (isTv) LinearLayoutManager.VERTICAL else LinearLayoutManager.HORIZONTAL,
            false,
        )
        sidebar.adapter = sidebarAdapter

        // El dropdown de playlists vive en el selector de 48dp (móvil y TV);
        // el rail queda solo para filtros. TV usa diálogo D-pad, móvil
        // PopupMenu.
        view.findViewById<TextView>(R.id.browser_list_selector)?.let { selector ->
            FocusScaleHelper.attach(selector)
            selector.setOnClickListener {
                if (isTv) showListDialog() else showListMenu(selector)
            }
        }

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

    /**
     * Cambio de playlist: mueve la [ActiveList] global, resetea el filtro a
     * Todos y recarga todo (sidebar + hero + cortes + contenido).
     */
    private fun onListPicked(id: Long) {
        lifecycleScope.launch {
            val db = (requireActivity().application as TpApp).container.db
            ActiveList.set(db, id)
            listId = id
            viewModel.setListId(id)
            selection = Selection.All
            viewModel.select(Selection.All)
            reloadAll()
        }
    }

    /**
     * Cambio de filtro: no toca la lista, solo re-selecciona y recarga el
     * contenido paginado.
     */
    private fun onFilterPicked(sel: Selection) {
        selection = sel
        viewModel.select(sel)
        sidebarAdapter.updateSelection(sel)
        reloadContent()
    }

    /** Dropdown framework con las playlists (móvil; el rail es de filtros). */
    private fun showListMenu(anchor: View) {
        lifecycleScope.launch {
            val db = (requireActivity().application as TpApp).container.db
            val lists = db.listDao().all()
            if (lists.isEmpty()) return@launch
            val popup = PopupMenu(requireContext(), anchor)
            lists.forEachIndexed { index, list ->
                popup.menu.add(0, index, index, list.name.ifBlank { getString(R.string.lists_name) })
            }
            popup.setOnMenuItemClickListener { item ->
                onListPicked(lists[item.itemId].id)
                true
            }
            popup.show()
        }
    }

    /** Diálogo D-pad con las playlists (TV; mismo onListPicked que el móvil). */
    private fun showListDialog() {
        lifecycleScope.launch {
            val db = (requireActivity().application as TpApp).container.db
            val lists = db.listDao().all()
            if (lists.isEmpty()) return@launch
            AlertDialog.Builder(requireContext())
                .setItems(listDialogLabels(lists, getString(R.string.lists_name))) { _, which ->
                    onListPicked(lists[which].id)
                }
                .show()
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
            val filterRows = ChannelSidebar.buildFilterRows(
                groups,
                getString(R.string.browser_groups_all),
                getString(R.string.browser_history),
                getString(R.string.browser_no_group),
                historyCount,
            )
            sidebarAdapter.activeListId = listId
            sidebarAdapter.updateSelection(selection)
            // Rail solo filtros + playlist activa en el selector (TV y móvil
            // con selector); sin selector, rail completo (fallback).
            val selector = view.findViewById<TextView>(R.id.browser_list_selector)
            val activeName = lists.find { it.id == listId }?.name
                ?.ifBlank { getString(R.string.lists_name) }
                ?: getString(R.string.lists_name)
            if (FocusScaleHelper.isTv(view) && selector != null) {
                // TV: rail solo filtros y playlist en el selector.
                sidebarAdapter.submit(filterRows)
                selector.text = "$activeName ▾"
            } else if (selector == null) {
                // Fallback sin selector: rail completo, como siempre.
                sidebarAdapter.submit(ChannelSidebar.buildPlaylistRows(lists, listId) + filterRows)
            } else {
                // Móvil como hoy: rail solo filtros y playlist en el dropdown.
                sidebarAdapter.submit(filterRows)
                selector.text = "$activeName ▾"
            }
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
            // Cabecera del timeline: 6 cortes de 30 min desde el anchor, con la
            // misma ventana que las filas y siempre visible (S6).
            window = GuideWindow.now()
            val slotsView = view.findViewById<TextView>(R.id.browser_slots)
            slotsView.text = slotsText(window)
            slotsView.visibility = View.VISIBLE
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
     * Si la grilla aún no resolvió visibles (submit recién hecho), reintento
     * acotado con postDelayed de 200 ms hasta 2 veces.
     */
    private fun fillTimeline(attempt: Int = 0) {
        val grid = view?.findViewById<RecyclerView>(R.id.browser_grid) ?: return
        val layout = grid.layoutManager as? GridLayoutManager ?: return
        val first = layout.findFirstVisibleItemPosition()
        val last = layout.findLastVisibleItemPosition()
        if (first < 0 || last < first) {
            if (attempt < TIMELINE_MAX_RETRY) {
                grid.postDelayed({ fillTimeline(attempt + 1) }, TIMELINE_RETRY_DELAY_MS)
            }
            return
        }
        lifecycleScope.launch {
            val db = (requireActivity().application as TpApp).container.db
            val ids = (first..last).mapNotNull { gridAdapter.peek(it)?.tvgId?.takeIf { t -> t.isNotBlank() } }
                .distinct()
            // Paging aún resolviendo el peek con grilla cargada: reintenta.
            if (ids.isEmpty()) {
                if (gridAdapter.itemCount > 0 && attempt < TIMELINE_MAX_RETRY) {
                    grid.postDelayed({ fillTimeline(attempt + 1) }, TIMELINE_RETRY_DELAY_MS)
                }
                return@launch
            }
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
