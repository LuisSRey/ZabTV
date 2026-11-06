package com.tvplayer.app

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
    }

    private fun goTab(tag: String) {
        when (tag) {
            HomeFragment.TAG -> open(HomeFragment(), HomeFragment.TAG)
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
            if (activeId != null) openBrowser(activeId) else openLists()
        }
    }

    private fun openSearchFirst() {
        lifecycleScopeLaunch {
            val db = (application as TpApp).container.db
            val activeId = com.tvplayer.app.data.ActiveList.resolve(db)
            if (activeId != null) {
                open(SearchFragment.newInstance(activeId), SearchFragment.TAG)
            } else {
                openLists()
            }
        }
    }

    private fun lifecycleScopeLaunch(block: suspend () -> Unit) {
        lifecycleScope.launch { block() }
    }

    /** EPG de la lista activa al arrancar: silenciosa salvo éxito con programas. */
    private fun refreshActiveEpg() {
        lifecycleScopeLaunch {
            val app = application as TpApp
            val db = app.container.db
            val listId = com.tvplayer.app.data.ActiveList.resolve(db) ?: return@lifecycleScopeLaunch
            val epgUrl = db.listDao().get(listId)?.epgUrl
            if (epgUrl.isNullOrBlank()) return@lifecycleScopeLaunch
            val res = runCatching {
                com.tvplayer.app.data.repo.EpgService(applicationContext, db, app.container.okHttp)
                    .refresh(epgUrl)
            }.getOrNull() ?: return@lifecycleScopeLaunch
            if (!res.rejected && res.inserted > 0) {
                Toast.makeText(this, getString(R.string.epg_refreshed, res.inserted), Toast.LENGTH_SHORT).show()
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

    override fun open(fragment: androidx.fragment.app.Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment, tag)
            .commitAllowingStateLoss()
        // open() no toca el back stack → el listener no dispara: sincronizar a mano.
        supportFragmentManager.executePendingTransactions()
        syncShell()
    }

    override fun openPlayer(listId: Long, position: Int) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, PlayerFragment.newInstance(listId, position), PlayerFragment.TAG)
            .addToBackStack(PlayerFragment.TAG)
            .commitAllowingStateLoss()
    }

    override fun openBrowser(listId: Long) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, ChannelBrowserFragment.newInstance(listId), ChannelBrowserFragment.TAG)
            .addToBackStack(ChannelBrowserFragment.TAG)
            .commitAllowingStateLoss()
    }

    override fun openLists() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, ListsFragment(), ListsFragment.TAG)
            .addToBackStack(ListsFragment.TAG)
            .commitAllowingStateLoss()
    }

    override fun openSettings() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, SettingsFragment(), SettingsFragment.TAG)
            .addToBackStack(SettingsFragment.TAG)
            .commitAllowingStateLoss()
    }
}

interface TvNavigator {
    interface NavHost {
        fun open(fragment: androidx.fragment.app.Fragment, tag: String)
        fun openPlayer(listId: Long, position: Int)
        fun openBrowser(listId: Long)
        fun openLists()
        fun openSettings()
    }
}
