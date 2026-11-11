package com.tvplayer.app.ui.channels

import android.content.res.Configuration
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvplayer.app.MainActivity
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.core.xmltv.GuideWindow
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.ListEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CA2: rotación sin recrear (configChanges) — ejecutar
 * onConfigurationChanged re-pinta todo: ventana NUEVA + cortes + hero +
 * timeline re-disparado. Monta el fragment real con DB sembrada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserRotateTest {

    private val t0 = 1_794_294_600_000L // 2026-11-10T19:10:00Z
    private val t1 = t0 + 2 * 3_600_000L // +2 h: otro bucket de 30 min seguro

    private fun pumpUntil(timeoutMs: Long = 20_000, cond: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) break
            Thread.sleep(25)
            looper.idle()
        }
        check(cond()) { "condición sin cumplir tras ${timeoutMs}ms" }
    }

    /**
     * Lectura de la cabecera por tipo (el fragment pinta por tipo): TextView
     * → text de una pieza; ViewGroup (slots 6 cortes) → join de los hijos
     * TextView con el mismo separador que slotsText.
     */
    private fun slotsTextOf(root: View): String =
        when (val v = root.findViewById<View>(R.id.browser_slots)) {
            is TextView -> v.text.toString()
            is ViewGroup -> (0 until v.childCount)
                .map { v.getChildAt(it) as? TextView }
                .filterNotNull()
                .joinToString("  ·  ") { it.text.toString() }
            else -> ""
        }

    private fun seed(listId: Long, db: com.tvplayer.app.data.db.AppDatabase) {        runBlocking {
            db.channelDao().upsertAll(
                listOf(
                    ChannelEntity(
                        listId = listId, tvgId = "c1", displayName = "Uno",
                        normName = "uno", groupName = "G", url = "https://s/1",
                        stableKey = "c1", position = 0,
                    ),
                    ChannelEntity(
                        listId = listId, tvgId = "c2", displayName = "Dos",
                        normName = "dos", groupName = "G", url = "https://s/2",
                        stableKey = "c2", position = 1,
                    ),
                ),
            )
        }
    }

    @Test
    fun `onConfigurationChanged renueva ventana slots hero y timeline`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val db = (activity.application as TpApp).container.db
        val listId = runBlocking {
            db.listDao().upsert(ListEntity(name = "L", sourceUrl = "https://x/lista.m3u"))
        }
        seed(listId, db)

        val fragment = ChannelBrowserFragment.newInstance(listId)
        fragment.clock = { t0 }
        activity.openTab(fragment, ChannelBrowserFragment.TAG)

        val root = fragment.requireView()
        val slots = root.findViewById<View>(R.id.browser_slots)
        val hero = root.findViewById<View>(R.id.browser_hero)
        val heroTitle = root.findViewById<TextView>(R.id.browser_hero_title)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)

        // Carga inicial con el reloj fijo en t0.
        pumpUntil { fragment.gridAdapter.itemCount == 2 && slotsTextOf(root).isNotEmpty() }
        val expected0 = ChannelBrowserFragment.slotsText(GuideWindow.now(t0))
        assertEquals(expected0, slotsTextOf(root))
        pumpUntil { hero.visibility == View.VISIBLE }
        assertEquals("Uno", heroTitle.text.toString())

        // Rotación 2 h después: ejecuta el camino real (configChanges).
        fragment.clock = { t1 }
        fragment.onConfigurationChanged(fragment.resources.configuration)

        val expected1 = ChannelBrowserFragment.slotsText(GuideWindow.now(t1))
        assertNotEquals(expected0, expected1)
        // Ventana nueva: los cortes se re-pintan con la ventana de t1.
        pumpUntil { slotsTextOf(root) == expected1 }
        assertEquals(View.VISIBLE, slots.visibility)
        // Hero re-pintado con el mismo destacado.
        pumpUntil { hero.visibility == View.VISIBLE && heroTitle.text.toString() == "Uno" }
        // Layouts reajustados (1 columna + orientación del rail vigente).
        val orientation = fragment.resources.configuration.orientation
        val isTv = root.resources.configuration.uiMode and
            Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
        assertEquals(1, (grid.layoutManager as GridLayoutManager).spanCount)
        assertEquals(
            ChannelBrowserFragment.sidebarOrientation(isTv, orientation),
            (root.findViewById<RecyclerView>(R.id.browser_sidebar).layoutManager as LinearLayoutManager).orientation,
        )
        // Timeline re-disparado con la ventana nueva: el adapter recibió
        // setWindow(t1) y las filas pintan la tira VISIBLE.
        pumpUntil { fragment.gridAdapter.currentWindow?.anchorMillis == GuideWindow.anchorFor(t1) }
        grid.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        grid.layout(0, 0, 1080, 1920)
        shadowOf(Looper.getMainLooper()).idle()
        val child = grid.getChildAt(0)
        val holder = grid.getChildViewHolder(child) as ChannelGridAdapter.Holder
        assertEquals(View.VISIBLE, holder.timeline?.visibility)
    }
}
