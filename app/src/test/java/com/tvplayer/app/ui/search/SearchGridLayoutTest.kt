package com.tvplayer.app.ui.search

import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.MainActivity
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.data.db.ListEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * SECCION-09 (T-P1-3 · D7/A1-01, cierra A1-04): Search = 1 COLUMNA con el
 * ítem-fila del browser (mismo layout que el browser, spanCount 1 en TODOS
 * los formatos). En móvil portrait (360dp) la fila completa de ancho deja de
 * desbordar (36+64+120dp + timeline weight). item_channel no se toca.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchGridLayoutTest {

    @Test
    fun `a costura columns es 1 en ambos formatos`() {
        assertEquals(1, SearchFragment.columns(isTv = true))
        assertEquals(1, SearchFragment.columns(isTv = false))
    }

    private fun pumpUntil(timeoutMs: Long = 15_000, cond: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            check(System.currentTimeMillis() <= end) { "efecto sin observar" }
            looper.runToNextTask()
            Thread.sleep(20)
        }
    }

    private fun assertOneColumn(activity: MainActivity) {
        val root = activity.supportFragmentManager
            .findFragmentById(R.id.container) as SearchFragment
        val view = root.requireView()
        val grid = view.findViewById<RecyclerView>(R.id.search_grid)
        pumpUntil { grid.layoutManager != null }
        assertTrue(
            "search debe usar LinearLayoutManager (filas como el browser)",
            grid.layoutManager is LinearLayoutManager,
        )
        val lm = grid.layoutManager as LinearLayoutManager
        assertEquals(RecyclerView.VERTICAL, lm.orientation)
    }

    @Test
    fun `b movil search 1 columna`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val container = (activity.application as TpApp).container
        val listId = runBlocking {
            container.db.listDao().upsert(ListEntity(name = "L", sourceUrl = "https://x/lista.m3u"))
        }
        activity.pushFlow(SearchFragment.newInstance(listId), SearchFragment.TAG)
        pumpUntil { activity.supportFragmentManager.findFragmentById(R.id.container) is SearchFragment }
        assertOneColumn(activity)
    }

    @Test
    @Config(sdk = [34], qualifiers = "television")
    fun `b2 tv search 1 columna`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val container = (activity.application as TpApp).container
        val listId = runBlocking {
            container.db.listDao().upsert(ListEntity(name = "L", sourceUrl = "https://x/lista.m3u"))
        }
        activity.pushFlow(SearchFragment.newInstance(listId), SearchFragment.TAG)
        pumpUntil { activity.supportFragmentManager.findFragmentById(R.id.container) is SearchFragment }
        assertOneColumn(activity)
    }
}
