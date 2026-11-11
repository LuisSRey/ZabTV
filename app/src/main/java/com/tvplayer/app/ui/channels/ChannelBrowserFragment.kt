package com.tvplayer.app.ui.channels

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ProgressBar
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
import com.tvplayer.app.ui.common.MarqueeOnFocus
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
 * (columna media 200dp con selector encima de los filtros + contenido, safe
 * area 56dp). Foco TV vía
 * [FocusScaleHelper] 1.1. La grilla es una lista de 1 columna y JAMÁS carga
 * la lista completa en RAM (PagingData; Historial = snapshot cap 20).
 */
class ChannelBrowserFragment : Fragment() {

    companion object {
        const val TAG = "browser"
        const val ARG_LIST_ID = "listId"

        /** Hero compacto móvil (S5): cap de altura, solo no-TV. */
        const val HERO_MAX_DP = 140

        /** Reintento acotado del timeline: tope de reintentos tras grid vacía. */
        const val TIMELINE_MAX_RETRY = BrowserTimelineRunner.MAX_RETRY

        /** Espera entre reintentos del timeline. */
        const val TIMELINE_RETRY_DELAY_MS = 200L

        /**
         * Etiquetas de la cabecera del timeline: 6 cortes "HH:mm" de la
         * [window] vigente (la misma que pinta las filas). Puro para test
         * directo. En vertical se unen con "·" ([slotsText]); en apaisado
         * cada una va en su corte (browser_slot_0..5).
         */
        fun slotsLabels(
            window: GuideWindow,
            locale: java.util.Locale = java.util.Locale.getDefault(),
        ): List<String> {
            val fmt = java.text.SimpleDateFormat("HH:mm", locale)
            return window.slots().map { fmt.format(java.util.Date(it)) }
        }

        /**
         * Cabecera del timeline: 6 cortes "HH:mm" de la misma [window] que
         * las filas (S6). Puro para test directo.
         */
        fun slotsText(
            window: GuideWindow,
            locale: java.util.Locale = java.util.Locale.getDefault(),
        ): String = slotsLabels(window, locale).joinToString("  ·  ")

        /**
         * Etiquetas del diálogo de playlists (TV): nombre de cada lista,
         * con fallback a `lists_name` si viene en blanco. Puro para test
         * directo.
         */
        fun listDialogLabels(lists: List<ListEntity>, fallback: String): Array<String> =
            ListPicker.labels(lists, fallback).toTypedArray()

        /**
         * Nombre de la lista activa para el selector (O2): nombre vigente
         * con fallback si viene en blanco, ausente o sin listas. Puro.
         */
        fun activeLabel(lists: List<ListEntity>, listId: Long, fallback: String): String =
            lists.find { it.id == listId }?.name?.ifBlank { fallback } ?: fallback

        /**
         * Texto del selector (O2): la etiqueta activa SIEMPRE con sufijo
         * ▾ (haya o no listas: el fallback ya viene resuelto). Puro.
         */
        fun selectorText(active: String): String = "$active ▾"

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
     * Hero colapsado (S1, land móvil): el chevron oculta el body y deja la
     * cabecera; sobrevive a la rotación vía snapshot (S2).
     */
    @VisibleForTesting
    internal var heroCollapsed: Boolean = false

    /**
     * Cableado único (S2): load-listener + colección Paging + resolve
     * inicial solo corren en el primer bind; el reinflate reutiliza todo.
     */
    private var boundOnce: Boolean = false

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

    /**
     * Orientación del último bind (S2): con ella se decide si el giro exige
     * reinflar (móvil portrait<->land) o basta managers + repaint (TV o
     * mismo layout).
     */
    @VisibleForTesting
    internal var lastOrientation: Int = Configuration.ORIENTATION_UNDEFINED

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_channel_browser, container, false)
    }

    /** Rotación sin recrear (configChanges): ventana + cortes + hero + timeline al día. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val root = view ?: return
        val isTv = FocusScaleHelper.isTv(root)
        val newOrientation = newConfig.orientation
        // S2: giro real en móvil = snapshot → reinflate síncrono (misma raíz
        // con los hijos del layout que toca) reutilizando adapter + Paging;
        // restore + repaint corren aquí mismo. TV o mismo layout: solo
        // managers + repaint.
        if (BrowserRotateHelper.needsReinflate(isTv, lastOrientation, newOrientation)) {
            reinflateOnRotate(newOrientation)
            return
        }
        lastOrientation = newOrientation
        repaintOnRotate()
    }

    /**
     * Re-pintado tras rotar sin giro de layout (S4 + S2 TV): el layout-land
     * solo entra en creaciones frescas (con configChanges no hay re-inflate),
     * así que aquí se reajusta a mano — columnas, orientación del rail,
     * ventana nueva, cortes, hero y re-disparo del timeline.
     */
    private fun repaintOnRotate() {
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

    /** Cap del hero compacto en px (S1). */
    @VisibleForTesting
    internal fun heroMaxPx(): Int =
        (HERO_MAX_DP * resources.displayMetrics.density).toInt()

    /**
     * Estado colapsado al hero land (S1): body GONE + chevron; sin body
     * (portrait/TV) no toca nada.
     */
    private fun applyHeroCollapsed(root: View) {
        val body = root.findViewById<View>(R.id.browser_hero_body) ?: return
        val toggle = root.findViewById<ImageButton>(R.id.browser_hero_toggle)
        body.visibility = if (heroCollapsed) View.GONE else View.VISIBLE
        toggle?.setImageResource(
            if (heroCollapsed) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up,
        )
        toggle?.contentDescription = getString(
            if (heroCollapsed) R.string.browser_hero_expand else R.string.browser_hero_collapse,
        )
    }

    /**
     * Cap 140dp del hero móvil (S5): si el contenido expandido desborda, se
     * fija la altura para que la grid conserve la suya. TV intacto (sin cap).
     */
    private fun bindHeroCap(heroBox: View) {
        heroBox.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val max = heroMaxPx()
            if (v.height > max && v.layoutParams.height != max) {
                v.layoutParams.height = max
                v.requestLayout()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val argId = arguments?.getLong(ARG_LIST_ID, -1L) ?: -1L
        val isTv = FocusScaleHelper.isTv(view)
        val container = (requireActivity().application as TpApp).container
        // S2: viewModel + adapters sobreviven al reinflate — solo se crean
        // en el primer bind y el Paging sigue coleccionado sobre el mismo
        // adapter (el viewLifecycleOwner no se destruye al trasplantar).
        if (!::viewModel.isInitialized) {
            viewModel = ChannelBrowserViewModel(
                argId,
                container.db.channelDao(),
                container.db.epgDao(),
                container.db.recentDao(),
            )
        }
        if (!::gridAdapter.isInitialized) {
            gridAdapter = ChannelGridAdapter(
                onClick = ::onChannelClick,
                onLongClick = { channel ->
                    ChannelSheetFragment.newInstance(channel.listId, channel.stableKey)
                        .show(parentFragmentManager, ChannelSheetFragment.TAG)
                },
            )
        }
        if (!::sidebarAdapter.isInitialized) {
            sidebarAdapter = ChannelSidebarAdapter(
                onPlaylist = ::onListPicked,
                onSelection = ::onFilterPicked,
            )
        }
        bindBrowserViews(view, isTv, resources.configuration.orientation)
        if (!boundOnce) {
            boundOnce = true
            // Vacío en All/Group por estado de carga (Historial lo resuelve en
            // reloadContent: loadState no emite para PagingData.from en tests).
            // Con datos (NotLoading + items) se pinta el timeline tras el
            // submit. Una sola vez: usa la vista vigente, no la del bind.
            gridAdapter.addLoadStateListener { states ->
                if (selection is Selection.History) return@addLoadStateListener
                val current = this.view ?: return@addLoadStateListener
                val resolved = states.refresh is LoadState.NotLoading
                val empty = current.findViewById<TextView>(R.id.browser_empty)
                // Vacío SOLO con 0 canales confirmados (nada rancio en carga).
                empty.visibility =
                    if (emptyVisible(gridAdapter.itemCount, resolved)) View.VISIBLE else View.GONE
                timelineRunner.onLoadState(resolved, gridAdapter.itemCount, selection is Selection.History)
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
        lastOrientation = resources.configuration.orientation
    }

    /**
     * Cablea la jerarquía vigente a los colaboradores (S2): managers +
     * adapters reutilizados + runner nuevo (captura la grid vigente) +
     * listeners de la vista + selector + toggle + cap. Vale para el primer
     * bind y para el re-bind tras reinflar.
     */
    private fun bindBrowserViews(root: View, isTv: Boolean, orientation: Int) {
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        grid.layoutManager = GridLayoutManager(requireContext(), spanCount(isTv, orientation))
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
            debugOf = { reason, attempt -> TimelineDebug.logEmpty(reason, attempt, window) },
        )
        // Tira ahora/siguiente + timeline por pausa de scroll (doble batch).
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                timelineRunner.onScrollStateChanged(newState)
            }
        })
        val sidebar = root.findViewById<RecyclerView>(R.id.browser_sidebar)
        sidebar.layoutManager = LinearLayoutManager(
            requireContext(),
            sidebarOrientation(isTv, orientation),
            false,
        )
        sidebar.adapter = sidebarAdapter

        // El dropdown de playlists vive en el selector de 48dp (móvil y TV);
        // el rail queda solo para filtros. TV usa diálogo D-pad, móvil
        // burbuja (ListPopupWindow modal).
        root.findViewById<TextView>(R.id.browser_list_selector)?.let { selector ->
            FocusScaleHelper.attach(selector)
            MarqueeOnFocus.attach(selector)
            selector.setOnClickListener {
                if (isTv) showListDialog() else showListMenu(selector)
            }
        }

        // Hero land colapsable con chevron (S1, solo donde existe el toggle)
        // + cap 140dp no-TV para que la grid conserve altura.
        root.findViewById<ImageButton>(R.id.browser_hero_toggle)?.let { toggle ->
            FocusScaleHelper.attach(toggle)
            applyHeroCollapsed(root)
            toggle.setOnClickListener {
                heroCollapsed = !heroCollapsed
                this.view?.let(::applyHeroCollapsed)
            }
        }
        if (!isTv) {
            root.findViewById<View>(R.id.browser_hero)?.let(::bindHeroCap)
        }
    }

    /**
     * Reinflate síncrono tras giro real en móvil (S2): snapshot → misma raíz
     * con los hijos del layout que toca → bind (adapters + Paging
     * reutilizados, sin re-coleccionar) → restore del scroll → ventana +
     * cortes + hero + fill(0). TV nunca llega aquí (needsReinflate).
     */
    private fun reinflateOnRotate(newOrientation: Int) {
        val root = view as? ViewGroup ?: return
        val snapshot = BrowserRotateHelper.snapshot(
            heroCollapsed,
            firstVisiblePosition(R.id.browser_grid),
            firstVisiblePosition(R.id.browser_sidebar),
            root.findViewById<TextView>(R.id.browser_list_selector)?.text?.toString().orEmpty(),
        )
        val fresh = layoutInflater.inflate(
            R.layout.fragment_channel_browser, root.parent as? ViewGroup, false,
        ) as? ViewGroup ?: return
        if (root is LinearLayout && fresh is LinearLayout) root.orientation = fresh.orientation
        root.setPadding(fresh.paddingLeft, fresh.paddingTop, fresh.paddingRight, fresh.paddingBottom)
        root.removeAllViews()
        while (fresh.childCount > 0) {
            val child = fresh.getChildAt(0)
            fresh.removeViewAt(0)
            root.addView(child)
        }
        val isTv = FocusScaleHelper.isTv(root)
        bindBrowserViews(root, isTv, newOrientation)
        root.findViewById<RecyclerView>(R.id.browser_grid)?.scrollToPosition(snapshot.gridPosition)
        root.findViewById<RecyclerView>(R.id.browser_sidebar)?.scrollToPosition(snapshot.sidebarPosition)
        // Restore del selector sin re-query (O2): el texto ya resuelto viaja
        // en el snapshot; vacío = placeholder del XML recién inflado.
        if (snapshot.selectorText.isNotEmpty()) {
            root.findViewById<TextView>(R.id.browser_list_selector)?.text = snapshot.selectorText
        }
        lastOrientation = newOrientation
        lifecycleScope.launch {
            if (listId < 0) return@launch
            window = GuideWindow.now(clock())
            paintSlots()
            paintHero()
            root.post { timelineRunner.fill() }
        }
    }

    /** Primera posición visible del layout (0 sin layout o sin items). */
    private fun firstVisiblePosition(recyclerId: Int): Int =
        (view?.findViewById<RecyclerView>(recyclerId)
            ?.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition()
            ?.coerceAtLeast(0) ?: 0

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

    /** Burbuja con las playlists (móvil; el rail es de filtros). */
    private fun showListMenu(anchor: View) {
        lifecycleScope.launch {
            val db = (requireActivity().application as TpApp).container.db
            val lists = db.listDao().all()
            if (lists.isEmpty()) return@launch
            val labels = ListPicker.labels(lists, getString(R.string.lists_name))
            ListPicker.showPopup(anchor, labels) { onListPicked(lists[it].id) }
        }
    }

    /** Diálogo D-pad con las playlists (TV; mismo onListPicked que el móvil). */
    private fun showListDialog() {
        lifecycleScope.launch {
            val db = (requireActivity().application as TpApp).container.db
            val lists = db.listDao().all()
            if (lists.isEmpty()) return@launch
            val labels = ListPicker.labels(lists, getString(R.string.lists_name))
            ListPicker.showDialog(requireContext(), labels) { onListPicked(lists[it].id) }
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
            // con selector); sin selector, rail completo (fallback). El texto
            // sale de activeLabel/selectorText tras el resolve (O2): con o
            // sin listas siempre hay sufijo ▾.
            val selector = view.findViewById<TextView>(R.id.browser_list_selector)
            val text = selectorText(activeLabel(lists, listId, getString(R.string.lists_name)))
            if (FocusScaleHelper.isTv(view) && selector != null) {
                // TV: rail solo filtros y playlist en el selector.
                sidebarAdapter.submit(filterRows)
                selector.text = text
            } else if (selector == null) {
                // Fallback sin selector: rail completo, como siempre.
                sidebarAdapter.submit(ChannelSidebar.buildPlaylistRows(lists, listId) + filterRows)
            } else {
                // Móvil como hoy: rail solo filtros y playlist en el dropdown.
                sidebarAdapter.submit(filterRows)
                selector.text = text
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

    /**
     * Hero del destacado (ahora + progreso + siguiente; GONE sin canales).
     *
     * Política de repintado (U3 S5): repinta SOLO en lista/filtro/reload
     * ([reloadAll]/[reloadContent]), rotación
     * ([repaintOnRotate]/[reinflateOnRotate]) y reingreso al fragment. El
     * foco D-pad (N eventos) NUNCA repinta: el fragment no registra ningún
     * callback de foco hacia aquí (los [FocusScaleHelper] solo animan la
     * escala 1.1) — lo blinda [ChannelBrowserHeroInvariantTest].
     */
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
            applyHeroCollapsed(view)
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

    /**
     * Cabecera de cortes de la [window] vigente (siempre visible). Por tipo:
     * TextView (vertical/TV) → [slotsText] de una pieza; ViewGroup (land:
     * LinearLayout con spacer + 6 cortes) → [slotsLabels] corte a corte por
     * orden (el spacer no es TextView y se salta solo). Nunca
     * findViewById<TextView> directo: en land browser_slots NO es un
     * TextView y el cast rompería. El hero no toca esta cabecera (ni la
     * contiene ni la desplaza: van de hermanos en el layout).
     */
    private fun paintSlots() {
        val root = view ?: return
        when (val slotsView = root.findViewById<View>(R.id.browser_slots)) {
            is TextView -> {
                slotsView.text = slotsText(window)
                slotsView.visibility = View.VISIBLE
            }
            is ViewGroup -> {
                val labels = slotsLabels(window)
                var i = 0
                for (c in 0 until slotsView.childCount) {
                    val cell = slotsView.getChildAt(c) as? TextView ?: continue
                    if (i < labels.size) cell.text = labels[i]
                    i++
                }
                slotsView.visibility = View.VISIBLE
            }
            else -> Unit
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
        // Traza sin PII (G2 S5): contadores + tvgIds + ventana + visibilidades.
        TimelineDebug.logBatch(
            tvgIds = exactIds + approxIds,
            window = window,
            visibles = visibles.size,
            epg = lines.size,
            channels = perChannel.size,
            vis = visSnapshot(),
        )
        return BrowserTimelineRunner.Batch(lines, perChannel)
    }

    /**
     * Visibilidades del chrome para la traza (sin PII): hero/cabecera/vacío.
     */
    private fun visSnapshot(): String {
        val root = view ?: return "sin-vista"
        fun vis(id: Int) = when (root.findViewById<View>(id)?.visibility) {
            View.VISIBLE -> "V"
            View.GONE -> "G"
            View.INVISIBLE -> "I"
            else -> "?"
        }
        return "hero=${vis(R.id.browser_hero)} slots=${vis(R.id.browser_slots)} empty=${vis(R.id.browser_empty)}"
    }

    private fun onChannelClick(channel: ChannelEntity) {
        (activity as? TvNavigator.NavHost)?.openPlayer(channel.listId, channel.position)
    }
}
