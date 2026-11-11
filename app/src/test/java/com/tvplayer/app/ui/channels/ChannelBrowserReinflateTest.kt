package com.tvplayer.app.ui.channels

import android.content.res.Configuration
import android.os.Looper
import android.view.View
import android.widget.TextView
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S2: refreshOnRotate con giro real en móvil reinfla reutilizando adapter +
 * Paging (misma instancia, ventana nueva, slots + hero + timeline al día);
 * sin giro o en TV solo managers + repaint. TDD RED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserReinflateTest {

    private val t0 = 1_794_294_600_000L // 2026-11-10T19:10:00Z
    private val t1 = t0 + 2 * 3_600_000L // +2 h: otro bucket de 30 min seguro
    private val bigCount = 10_000
    private val bigTarget = 4_321

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

    private fun seed(listId: Long, db: com.tvplayer.app.data.db.AppDatabase) {
        runBlocking {
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

    /** Siembra 10k canales en tandas (nunca la lista completa en una sola query). */
    private fun seed10k(listId: Long, db: com.tvplayer.app.data.db.AppDatabase) {
        runBlocking {
            (0 until bigCount).chunked(1_000).forEach { chunk ->
                db.channelDao().upsertAll(
                    chunk.map { i ->
                        ChannelEntity(
                            listId = listId, tvgId = "tvg$i", displayName = "Canal $i",
                            normName = "canal $i", groupName = "G", url = "https://s/$i",
                            stableKey = "k$i", position = i,
                        )
                    },
                )
            }
        }
    }

    /** Pasada de layout real para que el manager resuelva visibles en Robolectric. */
    private fun measureLayout(grid: RecyclerView) {
        grid.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        grid.layout(0, 0, 1080, 1920)
    }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun firstVisible(grid: RecyclerView): Int =
        (grid.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: -1

    /**
     * Asienta un scroll en Robolectric: NUNCA un idle entre scrollToPosition y
     * su layout (el traversal del padre consumiría el pendiente a altura 0).
     * Orden por vuelta: idle (avanzan Paging/startup) → re-emitir scroll
     * (clobber de cualquier scroll tardío, p. ej. el scrollToPosition(0) del
     * reloadContent inicial) → layout que lo consume a tamaño real.
     */
    private fun pumpScrollUntil(grid: RecyclerView, target: Int, timeoutMs: Long = 30_000) {
        val end = System.currentTimeMillis() + timeoutMs
        measureLayout(grid)
        grid.scrollToPosition(target)
        while (firstVisible(grid) != target) {
            if (System.currentTimeMillis() > end) break
            Thread.sleep(25)
            idleMain()
            grid.scrollToPosition(target)
            measureLayout(grid)
        }
        measureLayout(grid)
        check(firstVisible(grid) == target) {
            "scroll no asentado en $target " +
                "(first=${firstVisible(grid)} children=${grid.childCount})"
        }
    }

    @Test
    fun `giro movil reinfla reutilizando adapter y repinta todo`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val db = (activity.application as TpApp).container.db
        val listId = runBlocking {
            db.listDao().upsert(ListEntity(name = "L", sourceUrl = "https://x/lista.m3u"))
        }
        seed(listId, db)

        val fragment = ChannelBrowserFragment.newInstance(listId)
        fragment.clock = { t0 }
        activity.openTab(fragment, ChannelBrowserFragment.TAG)
        pumpUntil { fragment.gridAdapter.itemCount == 2 }

        val adapterBefore = fragment.gridAdapter
        val slotsBefore = fragment.requireView()
            .findViewById<TextView>(R.id.browser_slots).text.toString()
        assertEquals(ChannelBrowserFragment.slotsText(GuideWindow.now(t0)), slotsBefore)

        // Giro real portrait -> land con 2 h más en el reloj.
        fragment.clock = { t1 }
        val rotated = Configuration(fragment.resources.configuration)
        rotated.orientation = Configuration.ORIENTATION_LANDSCAPE
        fragment.onConfigurationChanged(rotated)

        // Reinfla (nueva vista land) reutilizando el mismo adapter + Paging.
        pumpUntil {
            runCatching { fragment.requireView().findViewById<RecyclerView>(R.id.browser_grid) }
                .getOrNull() != null && fragment.gridAdapter === adapterBefore &&
                fragment.gridAdapter.itemCount == 2
        }
        // Ventana nueva: cortes + hero + timeline al día.
        val expected1 = ChannelBrowserFragment.slotsText(GuideWindow.now(t1))
        pumpUntil {
            fragment.requireView().findViewById<TextView>(R.id.browser_slots).text.toString() == expected1
        }
        val root = fragment.requireView()
        pumpUntil {
            root.findViewById<View>(R.id.browser_hero).visibility == View.VISIBLE &&
                root.findViewById<TextView>(R.id.browser_hero_title).text.toString() == "Uno"
        }
        pumpUntil { fragment.gridAdapter.currentWindow?.anchorMillis == GuideWindow.anchorFor(t1) }
    }

    /**
     * Reinflate con 10k: land frío → scroll a N → giro en vivo a portrait.
     * El adapter (+ Paging) es la MISMA instancia y el primer visible sigue
     * en N (snapshot → scrollToPosition del reinflate). Sin cambio de
     * comportamiento: solo ejerce el camino S2 a escala.
     */
    @Test
    @Config(qualifiers = "land")
    fun `reinflate 10k conserva scroll y reutiliza adapter`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val db = (activity.application as TpApp).container.db
        val listId = runBlocking {
            db.listDao().upsert(ListEntity(name = "L", sourceUrl = "https://x/lista.m3u"))
        }
        seed10k(listId, db)

        // Land frío: el primer bind ya nace apaisado.
        val fragment = ChannelBrowserFragment.newInstance(listId)
        fragment.clock = { t0 }
        activity.openTab(fragment, ChannelBrowserFragment.TAG)
        pumpUntil(timeoutMs = 90_000) { fragment.gridAdapter.itemCount == bigCount }
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, fragment.lastOrientation)

        // Scroll a N con pasadas de layout reales.
        var grid = fragment.requireView().findViewById<RecyclerView>(R.id.browser_grid)
        pumpScrollUntil(grid, bigTarget)

        // Giro en vivo a portrait: reinfla reutilizando adapter + Paging.
        val adapterBefore = fragment.gridAdapter
        fragment.clock = { t1 }
        val rotated = Configuration(fragment.resources.configuration)
        rotated.orientation = Configuration.ORIENTATION_PORTRAIT
        fragment.onConfigurationChanged(rotated)
        pumpUntil(timeoutMs = 90_000) {
            runCatching { fragment.requireView().findViewById<RecyclerView>(R.id.browser_grid) }
                .getOrNull() != null && fragment.gridAdapter === adapterBefore &&
                fragment.gridAdapter.itemCount == bigCount
        }
        grid = fragment.requireView().findViewById(R.id.browser_grid)
        // El reinflate restaura el snapshot con scrollToPosition síncrono: el
        // test NO scrollea aquí (enmascararía el restore) — solo asienta el
        // layout pendiente, sin idles que lo consumieran a altura 0.
        measureLayout(grid)
        assertEquals(bigTarget, firstVisible(grid))
    }
}
