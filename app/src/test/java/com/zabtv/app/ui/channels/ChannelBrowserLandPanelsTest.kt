package com.zabtv.app.ui.channels

import android.content.Context
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.MainActivity
import com.zabtv.app.R
import com.zabtv.app.TpApp
import com.zabtv.app.data.db.ChannelEntity
import com.zabtv.app.data.db.ListEntity
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
 * S1: browser apaisado en 2 paneles (izq 248dp título+selector+sidebar con
 * scroll propio; dcha peso 1 con hero compacto colapsable + cortes + grid con
 * scroll vertical) + portrait con grid min96. TDD RED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserLandPanelsTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun landRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
    }

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

    @Test
    @Config(qualifiers = "land")
    fun `land sidebar min96 y scroll propio`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = landRoot(context)
        val sidebar = root.findViewById<RecyclerView>(R.id.browser_sidebar)
        assertEquals(dp(context, 96), sidebar.minimumHeight)
        assertTrue(sidebar.isVerticalScrollBarEnabled)
    }

    @Test
    @Config(qualifiers = "land")
    fun `land grid min96 y scroll vertical`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = landRoot(context)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertEquals(dp(context, 96), grid.minimumHeight)
        assertTrue(grid.isVerticalScrollBarEnabled)
    }

    @Test
    @Config(qualifiers = "land")
    fun `land hero colapsable con chevron`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = landRoot(context)
        val toggle = root.findViewById<ImageButton>(R.id.browser_hero_toggle)
        assertTrue(toggle != null)
        assertTrue(toggle.isClickable)
        assertTrue(toggle.isFocusable)
        assertTrue(root.findViewById<View>(R.id.browser_hero_body) != null)
    }

    @Test
    fun `portrait grid min96`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = FrameLayout(context)
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertEquals(dp(context, 96), grid.minimumHeight)
    }

    @Test
    @Config(qualifiers = "land")
    fun `land 360x640 hero cap176 colapsa y grid con altura`() {
        // S5 (motivo S6): cap 180→176dp (el hero deja más grid en 360p;
        // 140→176 para contener el VER móvil sin recorte).
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val db = (activity.application as TpApp).container.db
        val listId = runBlocking {
            db.listDao().upsert(ListEntity(name = "L", sourceUrl = "https://x/lista.m3u"))
        }
        runBlocking {
            db.channelDao().upsertAll(
                listOf(
                    ChannelEntity(
                        listId = listId, tvgId = "c1", displayName = "Uno",
                        normName = "uno", groupName = "G", url = "https://s/1",
                        stableKey = "c1", position = 0,
                    ),
                ),
            )
        }
        val fragment = ChannelBrowserFragment.newInstance(listId)
        activity.openTab(fragment, ChannelBrowserFragment.TAG)
        val root = fragment.requireView()
        val hero = root.findViewById<View>(R.id.browser_hero)
        val heroNow = root.findViewById<TextView>(R.id.browser_hero_now)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        pumpUntil { hero.visibility == View.VISIBLE }

        // Hero expandido con contenido gigante: cap 176dp y la grid conserva altura.
        heroNow.text = (1..30).joinToString("\n") { "Línea de guía $it con texto largo para forzar altura del hero" }
        hero.requestLayout()
        shadowOf(Looper.getMainLooper()).idle()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(dp(root.context, 640), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(root.context, 360), View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, dp(root.context, 640), dp(root.context, 360))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(hero.height <= dp(root.context, 176))
        assertTrue(grid.height > 0)

        // Colapsable con chevron: el body se oculta y vuelve.
        val toggle = root.findViewById<ImageButton>(R.id.browser_hero_toggle)
        val body = root.findViewById<View>(R.id.browser_hero_body)
        toggle.performClick()
        assertEquals(View.GONE, body.visibility)
        toggle.performClick()
        assertEquals(View.VISIBLE, body.visibility)
    }
}
