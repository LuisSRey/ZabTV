package com.tvplayer.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.tvplayer.app.ui.channels.ChannelBrowserFragment
import com.tvplayer.app.ui.home.HomeFragment
import com.tvplayer.app.ui.lists.ListsFragment
import com.tvplayer.app.ui.player.PlayerFragment
import com.tvplayer.app.ui.search.SearchFragment
import com.tvplayer.app.ui.settings.SettingsFragment
import com.tvplayer.app.ui.common.FocusScaleHelper

/**
 * Única Activity (spec §5): shell estilo tvOS — sidebar en TV, barra inferior
 * en móvil/tablet — + contenedor de fragments. La selección y la visibilidad
 * del chrome se sincronizan con el back stack (syncShell), así BACK funciona
 * sin depender del foco.
 */
class MainActivity : AppCompatActivity(), TvNavigator.NavHost {

    private val tabs = listOf(
        R.id.nav_home to HomeFragment.TAG,
        R.id.nav_browse to ChannelBrowserFragment.TAG,
        R.id.nav_search to SearchFragment.TAG,
        R.id.nav_lists to ListsFragment.TAG,
        R.id.nav_settings to SettingsFragment.TAG,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tabs.forEach { (id, tag) ->
            val row = findViewById<View>(id) ?: return@forEach
            FocusScaleHelper.attach(row)
            row.setOnClickListener { goTab(tag) }
        }
        supportFragmentManager.addOnBackStackChangedListener { syncShell() }
        if (supportFragmentManager.findFragmentByTag(HomeFragment.TAG) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, HomeFragment(), HomeFragment.TAG)
                .commit()
        }
        supportFragmentManager.executePendingTransactions()
        syncShell()
        refreshActiveEpg()
        // "Abrir con" desde un gestor (solo creaciones frescas: con
        // configChanges no hay recreación por rotación que duplique).
        if (savedInstanceState == null) handleViewIntent(intent)
    }

    private fun goTab(tag: String) {
        when (tag) {
            HomeFragment.TAG -> openTab(HomeFragment(), HomeFragment.TAG)
            ChannelBrowserFragment.TAG -> openBrowserFirst()
            SearchFragment.TAG -> openSearchFirst()
            ListsFragment.TAG -> openLists()
            SettingsFragment.TAG -> openSettings()
        }
    }

    private fun openBrowserFirst() {
        lifecycleScopeLaunch {
            val db = (application as TpApp).container.db
            val activeId = com.tvplayer.app.data.ActiveList.resolve(db)
            // Tab: purga la pila (openTab), no apila flow.
            if (activeId != null) {
                openTab(
                    ChannelBrowserFragment.newInstance(activeId),
                    ChannelBrowserFragment.TAG,
                )
            } else {
                openLists()
            }
        }
    }

    private fun openSearchFirst() {
        lifecycleScopeLaunch {
            val db = (application as TpApp).container.db
            val activeId = com.tvplayer.app.data.ActiveList.resolve(db)
            if (activeId != null) {
                openTab(SearchFragment.newInstance(activeId), SearchFragment.TAG)
            } else {
                openLists()
            }
        }
    }

    private fun lifecycleScopeLaunch(block: suspend () -> Unit) {
        lifecycleScope.launch { block() }
    }

    /** EPG silenciosa (toast solo con programas): arranque y "abrir con". */
    private suspend fun refreshEpgQuiet(listId: Long) {
        val app = application as TpApp
        val db = app.container.db
        val epgUrl = db.listDao().get(listId)?.epgUrl
        if (epgUrl.isNullOrBlank()) return
        val res = runCatching {
            com.tvplayer.app.data.repo.EpgService(applicationContext, db, app.container.okHttp)
                .refresh(epgUrl)
        }.getOrNull() ?: return
        if (!res.rejected && res.inserted > 0) {
            Toast.makeText(this, getString(R.string.epg_refreshed, res.inserted), Toast.LENGTH_SHORT).show()
        }
    }

    /** EPG de la lista activa al arrancar: silenciosa salvo éxito con programas. */
    private fun refreshActiveEpg() {
        lifecycleScopeLaunch {
            val db = (application as TpApp).container.db
            val listId = com.tvplayer.app.data.ActiveList.resolve(db) ?: return@lifecycleScopeLaunch
            refreshEpgQuiet(listId)
        }
    }

    /**
     * "Abrir con" desde un gestor de archivos (Fire TV sin picker): importa
     * la m3u como lista nueva y abre su browser. Sin permisos extra: el grant
     * de lectura viene con el intent del gestor.
     */
    private fun handleViewIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        lifecycleScopeLaunch {
            val app = application as TpApp
            val db = app.container.db
            if (db.listDao().all().size >= ListsFragment.MAX_LISTS) {
                Toast.makeText(this, R.string.lists_max, Toast.LENGTH_LONG).show()
                return@lifecycleScopeLaunch
            }
            val listId = db.listDao().upsert(
                com.tvplayer.app.data.db.ListEntity(
                    name = uri.lastPathSegment ?: "Archivo",
                    sourceUrl = "", status = "importing",
                ),
            )
            val result = com.tvplayer.app.data.repo.ImportService(
                applicationContext, db, app.container.okHttp,
            ).import(listId, com.tvplayer.app.data.repo.ImportSource.FileSource(uri))
            Toast.makeText(
                this,
                result.message ?: getString(R.string.portal_import_error),
                Toast.LENGTH_LONG,
            ).show()
            if (!result.rejected) {
                refreshEpgQuiet(listId)
                openBrowser(listId)
            }
        }
    }

    /** Estado del shell según el fragment visible: tab activa + chrome. */
    private fun syncShell() {
        val current = supportFragmentManager.findFragmentById(R.id.container)
        val tag = current?.tag
        val accent = ContextCompat.getColor(this, R.color.tp_accent)
        val dim = ContextCompat.getColor(this, R.color.tp_text_secondary)
        val white = ContextCompat.getColor(this, R.color.tp_text)
        tabs.forEach { (id, t) ->
            val row = findViewById<View>(id) ?: return@forEach
            val active = (t == tag)
            row.isActivated = active
            row.findViewById<TextView>(labelId(id))?.setTextColor(if (active) white else dim)
            row.findViewById<ImageView>(iconId(id))?.setColorFilter(if (active) accent else dim)
        }
        val chrome = findViewById<View>(R.id.sidebar) ?: findViewById(R.id.bottom_bar)
        chrome?.visibility = if (current is PlayerFragment) View.GONE else View.VISIBLE
    }

    private fun labelId(rowId: Int): Int = when (rowId) {
        R.id.nav_home -> R.id.nav_home_label
        R.id.nav_browse -> R.id.nav_browse_label
        R.id.nav_search -> R.id.nav_search_label
        R.id.nav_lists -> R.id.nav_lists_label
        else -> R.id.nav_settings_label
    }

    private fun iconId(rowId: Int): Int = when (rowId) {
        R.id.nav_home -> R.id.nav_home_icon
        R.id.nav_browse -> R.id.nav_browse_icon
        R.id.nav_search -> R.id.nav_search_icon
        R.id.nav_lists -> R.id.nav_lists_icon
        else -> R.id.nav_settings_icon
    }

    /**
     * Tab (I1): purga la pila y reemplaza SIN apilar. BACK en raíz sale.
     */
    override fun openTab(fragment: androidx.fragment.app.Fragment, tag: String) {
        supportFragmentManager.popBackStackImmediate(
            null,
            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE,
        )
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment, tag)
            .commitAllowingStateLoss()
        // openTab no toca el back stack → el listener no dispara: sincronizar a mano.
        supportFragmentManager.executePendingTransactions()
        syncShell()
    }

    /**
     * Flow (I2): máx 1 entrada — pop previo si hay flow — + reemplazo apilado.
     * BACK en flow hace pop.
     */
    override fun pushFlow(fragment: androidx.fragment.app.Fragment, tag: String) {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStackImmediate()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment, tag)
            .addToBackStack(tag)
            .commitAllowingStateLoss()
        supportFragmentManager.executePendingTransactions()
    }

    @Deprecated("Usa openTab", ReplaceWith("openTab(fragment, tag)"))
    override fun open(fragment: androidx.fragment.app.Fragment, tag: String) =
        openTab(fragment, tag)

    /**
     * Player con auto-route externo (S5): con la política FRESCA de DAO
     * (allowHttp + global + preferido vigentes, no caché) se resuelve la
     * ruta — externo directo/chooser, o interno con toast si está vetado
     * (blocked), sin receptor (no_external) o con preferido desinstalado
     * (gone). Vetos de ExternalGate intactos.
     *
     * H1 (orden de seguridad): el veto corre sobre la URL CRUDA ANTES de
     * construir el VIEW o consultar al PackageManager — nada de intents ni
     * queries con URLs vetadas. No se reutiliza `ExternalOpener.openChannel`
     * porque devuelve solo Boolean y aquí cada ruta lleva su toast
     * (blocked/gone/no_external); reutilizarlo los perdería (cambio de
     * comportamiento). La doble resolución no duplica la secuencia del
     * gate: la primera es solo el veto, la segunda decide directo/chooser.
     */
    override fun openPlayer(listId: Long, position: Int) {
        lifecycleScopeLaunch {
            val app = application as TpApp
            val db = app.container.db
            val channel = db.channelDao().channelAtPosition(listId, position)
            if (channel == null) {
                pushFlow(PlayerFragment.newInstance(listId, position), PlayerFragment.TAG)
                return@lifecycleScopeLaunch
            }
            val (allowHttp, global, preferred) =
                com.tvplayer.app.engine.ExternalAutoRoute.freshPolicyForRoute(db.settingsDao())
            val proxy = com.tvplayer.app.data.AppContainer.DEFAULT_ACE_PROXY
            val engineTarget =
                com.tvplayer.app.engine.AceStreamEngine.engineProxyFor(channel.url, proxy)
            val source = try {
                com.tvplayer.app.engine.PlaybackSource.forUrl(
                    channel.url, allowCleartext = allowHttp, aceStreamProxy = proxy,
                )
            } catch (_: IllegalArgumentException) {
                null
            }
            fun openInternal() =
                pushFlow(PlayerFragment.newInstance(listId, position), PlayerFragment.TAG)
            // Veto primero (sin intent ni PM): con usable=false la ruta solo
            // distingue INTERNAL/BLOCKED de "externo querido".
            when (
                com.tvplayer.app.engine.ExternalAutoRoute.resolve(
                    channel.url, allowHttp, engineTarget, channel.allowExternal,
                    global, preferred, preferredUsable = false,
                )
            ) {
                com.tvplayer.app.engine.ExternalAutoRoute.Route.INTERNAL -> openInternal()
                com.tvplayer.app.engine.ExternalAutoRoute.Route.BLOCKED -> {
                    Toast.makeText(this, R.string.player_external_blocked, Toast.LENGTH_SHORT).show()
                    openInternal()
                }
                // La URL pasó el veto: recién aquí se construye el VIEW y se
                // consulta al PackageManager (preferido usable o chooser).
                com.tvplayer.app.engine.ExternalAutoRoute.Route.EXTERNAL_DIRECT,
                com.tvplayer.app.engine.ExternalAutoRoute.Route.EXTERNAL_CHOOSER,
                -> {
                    val viewIntent = com.tvplayer.app.engine.ExternalOpener.buildIntent(
                        channel.url, source?.mimeType(),
                    )
                    val preferredUsable = !preferred.isNullOrBlank() &&
                        com.tvplayer.app.engine.ExternalPlayers.isPreferredUsable(
                            packageManager, preferred, viewIntent,
                        )
                    when (
                        com.tvplayer.app.engine.ExternalAutoRoute.resolve(
                            channel.url, allowHttp, engineTarget, channel.allowExternal,
                            global, preferred, preferredUsable,
                        )
                    ) {
                        com.tvplayer.app.engine.ExternalAutoRoute.Route.EXTERNAL_DIRECT -> {
                            val opened = com.tvplayer.app.engine.ExternalOpener.openDirect(
                                this, viewIntent, requireNotNull(preferred),
                            )
                            if (!opened) {
                                Toast.makeText(this, R.string.player_no_external, Toast.LENGTH_SHORT).show()
                                openInternal()
                            }
                        }
                        com.tvplayer.app.engine.ExternalAutoRoute.Route.EXTERNAL_CHOOSER -> {
                            if (!preferred.isNullOrBlank() && !preferredUsable) {
                                Toast.makeText(
                                    this, R.string.settings_external_gone, Toast.LENGTH_LONG,
                                ).show()
                            }
                            val opened =
                                com.tvplayer.app.engine.ExternalOpener.openWithChooser(this, viewIntent)
                            if (!opened) {
                                Toast.makeText(this, R.string.player_no_external, Toast.LENGTH_SHORT).show()
                                openInternal()
                            }
                        }
                        // Inalcanzables (el veto ya pasó arriba): defensivo a interno.
                        com.tvplayer.app.engine.ExternalAutoRoute.Route.INTERNAL -> openInternal()
                        com.tvplayer.app.engine.ExternalAutoRoute.Route.BLOCKED -> {
                            Toast.makeText(this, R.string.player_external_blocked, Toast.LENGTH_SHORT).show()
                            openInternal()
                        }
                    }
                }
            }
        }
    }

    override fun openBrowser(listId: Long) =
        pushFlow(ChannelBrowserFragment.newInstance(listId), ChannelBrowserFragment.TAG)

    override fun openLists() = openTab(ListsFragment(), ListsFragment.TAG)

    override fun openSettings() = openTab(SettingsFragment(), SettingsFragment.TAG)
}

interface TvNavigator {
    interface NavHost {
        @Deprecated("Usa openTab", ReplaceWith("openTab(fragment, tag)"))
        fun open(fragment: androidx.fragment.app.Fragment, tag: String)
        fun openTab(fragment: androidx.fragment.app.Fragment, tag: String)
        fun pushFlow(fragment: androidx.fragment.app.Fragment, tag: String)
        fun openPlayer(listId: Long, position: Int)
        fun openBrowser(listId: Long)
        fun openLists()
        fun openSettings()
    }
}
