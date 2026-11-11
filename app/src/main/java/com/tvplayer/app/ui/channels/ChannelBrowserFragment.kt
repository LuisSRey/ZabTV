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
import androidx.annotation.VisibleForTesting
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
        const val TIMELINE_MAX_RETRY = BrowserTimelineRunner.MAX_RETRY

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

        /**
         * Vacío visible SOLO con 0 canales confirmados (refresh ya resolvió,
         * sin carga en curso): nada de "Sin datos" rancio mientras carga.
         * Puro para test directo.
         */
        fun emptyVisible(itemCount: Int, refreshNotLoading: Boolean): Boolean =
            refreshNotLoading && itemCount == 0

        /**
         * Orientación del rail: vertical en TV y en apaisado móvil (panel
         * izquierdo de 320dp); horizontal solo en vertical táctil. Puro.
         */
        fun sidebarOrientation(isTv: Boolean, orientation: Int): Int =
            if (isTv || orientation == Configuration.ORIENTATION_LANDSCAPE) {
                LinearLayoutManager.VERTICAL
            } else {
                LinearLayoutManager.HORIZONTAL
            }

        fun newInstance(listId: Long) = ChannelBrowserFragment().apply {
            arguments = bundleOf(ARG_LIST_ID to listId)
        }
    }

    private lateinit var viewModel: ChannelBrowserViewModel
    private var listId: Long = -1
    private var selection: Selection = Selection.All
    private lateinit var sidebarAdapter: ChannelSidebarAdapter
    @VisibleForTesting
    internal lateinit var gridAdapter: ChannelGridAdapter
    private var featured: ChannelEntity? = null
    private var window: GuideWindow = GuideWindow.now()

    /**
     * Reloj inyectable para tests (CA2): la ventana tras rotar se recomputa
     * de aquí; en producción es el reloj del sistema (sin cambio de
     * comportamiento).
     */
    @VisibleForTesting
    internal var clock: () -> Long = System::currentTimeMillis

    /** Orquestación del timeline (CA4): el fragment cablea vistas → runner. */
    @VisibleForTesting
    internal lateinit var timelineRunner: BrowserTimelineRunner

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_channel_browser, container, false)
    }

    /** Rotación sin recrear (configChanges): ventana + cortes + hero + timeline al día. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshOnRotate()
    }

    /**
     * Re-pintado tras rotar (S4): el layout-land solo entra en creaciones
     * frescas (con configChanges no hay re-inflate), así que aquí se reajusta
     * a mano — columnas, orientación del rail, ventana nueva, cortes, hero y
     * re-disparo del timeline.
     */
    private fun refreshOnRotate() {
        val root = view ?: return
        val isTv = FocusScaleHelper.isTv(root)
        val orientation = resources.configuration.orientation
        root.findViewById<RecyclerView>(R.id.browser_grid)?.layoutManager =
            GridLayoutManager(requireContext(), spanCount(isTv, orientation))
        root.findViewById<RecyclerView>(R.id.browser_sidebar)?.layoutManager =
            LinearLayoutManager(requireContext(), sidebarOrientation(isTv, orientation), false)
        lifecycleScope.launch {
            val v = view ?: return@launch
            if (listId < 0) return@launch
            window = GuideWindow.now(clock())
            paintSlots()
            paintHero()
            v.post { timelineRunner.fill() }
        }
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
        timelineRunner = BrowserTimelineRunner(
            scope = lifecycleScope,
            windowOf = { window },
            rangeOf = {
                val lm = grid.layoutManager as? GridLayoutManager ?: return@BrowserTimelineRunner null
                lm.findFirstVisibleItemPosition()..lm.findLastVisibleItemPosition()
            },
            peekOf = { gridAdapter.peek(it) },
            itemCountOf = { gridAdapter.itemCount },
            batchOf = ::timelineBatch,
            setWindow = gridAdapter::setWindow,
            submitEpg = gridAdapter::submitEpg,
            submitTimeline = gridAdapter::submitTimeline,
            retryOf = { next ->
                grid.postDelayed({ timelineRunner.fill(next) }, TIMELINE_RETRY_DELAY_MS)
            },
            maxRetry = TIMELINE_MAX_RETRY,
        )
        // Tira ahora/siguiente + timeline por pausa de scroll (doble batch).
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                timelineRunner.onScrollStateChanged(newState)
            }
        })
        // Vacío en All/Group por estado de carga (Historial lo resuelve en
        // reloadContent: loadState no emite para PagingData.from en tests). Con
        // datos (NotLoading + items) se pinta el timeline tras el submit.
        gridAdapter.addLoadStateListener { states ->
            if (selection is Selection.History) return@addLoadStateListener
            val resolved = states.refresh is LoadState.NotLoading
            val empty = view.findViewById<TextView>(R.id.browser_empty)
            // Vacío SOLO con 0 canales confirmados (nada rancio en carga).
            empty.visibility =
                if (emptyVisible(gridAdapter.itemCount, resolved)) View.VISIBLE else View.GONE
            timelineRunner.onLoadState(resolved, gridAdapter.itemCount, selection is Selection.History)
        }

        sidebarAdapter = ChannelSidebarAdapter(
            onPlaylist = ::onListPicked,
            onSelection = ::onFilterPicked,
        )
        val sidebar = view.findViewById<RecyclerView>(R.id.browser_sidebar)
        sidebar.layoutManager = LinearLayoutManager(
            requireContext(),
            sidebarOrientation(isTv, resources.configuration.orientation),
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
            paintHero()
            // Cabecera del timeline: 6 cortes de 30 min desde el anchor, con la
            // misma ventana que las filas y siempre visible (S6).
            window = GuideWindow.now(clock())
            paintSlots()
            reloadContent()
        }
    }

    /** Hero del destacado (ahora + progreso + siguiente; GONE sin canales). */
    private suspend fun paintHero() {
        val view = view ?: return
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
    }

    /** Cabecera de cortes de la [window] vigente (siempre visible). */
    private fun paintSlots() {
        val slotsView = view?.findViewById<TextView>(R.id.browser_slots) ?: return
        slotsView.text = slotsText(window)
        slotsView.visibility = View.VISIBLE
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
            view.post { timelineRunner.fill() }
        }
    }

    /**
     * Lote del timeline sobre los visibles → líneas "▶ ahora · después" +
     * programas de la ventana por canal (clave: stableKey). La clave EPG por
     * canal la resuelve [BrowserEpgResolver] (exacta con programas crudos →
     * fallback aproximado vía EpgMatcher → sin guía). La orquestación
     * (ventana-primero, submitTimeline siempre, reintento acotado) vive en
     * [BrowserTimelineRunner]; aquí solo el lote con DB + ViewModel.
     */
    private suspend fun timelineBatch(
        visibles: List<ChannelEntity>,
    ): BrowserTimelineRunner.Batch {
        val db = (requireActivity().application as TpApp).container.db
        val exactIds = visibles.mapNotNull { it.tvgId?.takeIf { t -> t.isNotBlank() } }
            .distinct()
        val now = System.currentTimeMillis()
        val nn = db.epgDao().nowNext(exactIds, now).toMutableMap()
        val range = viewModel.timeline(exactIds, window).toMutableMap()
        // Fallback aproximado SOLO en misses exactos (acota las queries).
        val matcher = com.tvplayer.app.data.repo.EpgMatcher(db.epgDao())
        val approxHit = mutableMapOf<String, String>()
        for (ch in visibles) {
            if (BrowserEpgResolver.resolveKey(ch.tvgId, range, null) != null) continue
            val hit = matcher.match(ch) ?: continue
            approxHit[ch.stableKey] = hit.tvgId
        }
        val approxIds = approxHit.values.distinct().filter { !range.containsKey(it) }
        if (approxIds.isNotEmpty()) {
            nn.putAll(db.epgDao().nowNext(approxIds, now))
            range.putAll(viewModel.timeline(approxIds, window))
        }
        val lines = mutableMapOf<String, String>()
        val perChannel = mutableMapOf<String, List<EpgProgramEntity>>()
        for (ch in visibles) {
            val key = BrowserEpgResolver.resolveKey(ch.tvgId, range, approxHit[ch.stableKey])
                ?: continue
            val (nowP, nextP) = nn[key] ?: (null to null)
            listOfNotNull(
                nowP?.title?.let { "▶ $it" },
                nextP?.title?.let { "· $it" },
            ).joinToString(" ").takeIf { it.isNotBlank() }?.let { lines[ch.stableKey] = it }
            range[key]?.takeIf { it.isNotEmpty() }?.let { perChannel[ch.stableKey] = it }
        }
        return BrowserTimelineRunner.Batch(lines, perChannel)
    }

    private fun onChannelClick(channel: ChannelEntity) {
        (activity as? TvNavigator.NavHost)?.openPlayer(channel.listId, channel.position)
    }
}
