package com.tvplayer.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tvplayer.app.ui.channels.ChannelBrowserFragment
import com.tvplayer.app.ui.home.HomeFragment
import com.tvplayer.app.ui.lists.ListsFragment
import com.tvplayer.app.ui.player.PlayerFragment
import com.tvplayer.app.ui.settings.SettingsFragment

/**
 * Única Activity de la app (spec §5). Los cambios de pantalla son replacements de Fragment.
 * IMPORTANTE (spec §1): primera pantalla sin listas muestra el disclaimer de que la app
 * no incluye contenido — se materializa en el layout de Home.
 */
class MainActivity : AppCompatActivity(), TvNavigator.NavHost {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (supportFragmentManager.findFragmentByTag(HomeFragment.TAG) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, HomeFragment(), HomeFragment.TAG)
                .commit()
        }
    }

    override fun open(fragment: androidx.fragment.app.Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment, tag)
            .commitAllowingStateLoss()
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
