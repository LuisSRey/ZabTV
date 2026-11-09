package com.tvplayer.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.tvplayer.app.ui.home.HomeFragment
import com.tvplayer.app.ui.lists.ListsFragment
import com.tvplayer.app.ui.settings.SettingsFragment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityNavTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openTab_nuncaApila() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.openTab(HomeFragment(), HomeFragment.TAG)
        Robolectric.flushForegroundThreadScheduler()
        activity.openTab(ListsFragment(), ListsFragment.TAG)
        Robolectric.flushForegroundThreadScheduler()
        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
        assertEquals(
            ListsFragment.TAG,
            activity.supportFragmentManager.findFragmentById(R.id.container)?.tag,
        )
    }

    @Test
    fun pushFlow_maxUnaEntrada() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.pushFlow(ListsFragment(), ListsFragment.TAG)
        Robolectric.flushForegroundThreadScheduler()
        activity.pushFlow(SettingsFragment(), SettingsFragment.TAG)
        Robolectric.flushForegroundThreadScheduler()
        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
        assertEquals(
            SettingsFragment.TAG,
            activity.supportFragmentManager.findFragmentById(R.id.container)?.tag,
        )
    }

    @Test
    fun backEnRaiz_sale() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.openTab(HomeFragment(), HomeFragment.TAG)
        Robolectric.flushForegroundThreadScheduler()
        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun f1_1_journeyTabHomeBack_saleSinFusion() {
        // Home → flow → flow → tab Home: la tab purga huérfanos (sin fusión
        // de vistas) y BACK sale. Stand-ins ligeros: Player/Browser reales
        // arrancan motor/DB en onViewCreated y no son aptos para Robolectric.
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.openTab(HomeFragment(), HomeFragment.TAG)
        Robolectric.flushForegroundThreadScheduler()
        activity.pushFlow(ListsFragment(), ListsFragment.TAG)
        Robolectric.flushForegroundThreadScheduler()
        activity.pushFlow(SettingsFragment(), SettingsFragment.TAG)
        Robolectric.flushForegroundThreadScheduler()
        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
        activity.openTab(HomeFragment(), HomeFragment.TAG)
        Robolectric.flushForegroundThreadScheduler()
        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
        assertEquals(
            HomeFragment.TAG,
            activity.supportFragmentManager.findFragmentById(R.id.container)?.tag,
        )
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun f1_2_tapsRepetidosEnTabs_noAcumulan() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        repeat(3) {
            activity.openTab(HomeFragment(), HomeFragment.TAG)
            Robolectric.flushForegroundThreadScheduler()
            activity.openTab(ListsFragment(), ListsFragment.TAG)
            Robolectric.flushForegroundThreadScheduler()
        }
        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
        assertEquals(
            ListsFragment.TAG,
            activity.supportFragmentManager.findFragmentById(R.id.container)?.tag,
        )
    }
}
