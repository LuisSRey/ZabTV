package com.tvplayer.app.ui.channels

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Y1 S2 (TV+escudo): hero a match_parent sin minWidth; interior,
 * nextFocus, cap y GONE intactos. Con hero GONE el wrap conserva ≥192dp,
 * los slots quedan inmóviles y la grid apunta al selector; con hero
 * visible la grid apunta al VER. Solo layout-television (+ réplica).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserY2HeroTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun tvRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
    }

    @Test
    @Config(qualifiers = "television")
    fun `Y1 S2 tv hero match_parent sin minWidth`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val hero = root.findViewById<View>(R.id.browser_hero)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, hero.layoutParams.width)
        assertEquals(0, hero.minimumWidth)
    }

    @Test
    @Config(qualifiers = "land-television")
    fun `Y1 S2 escudo hero match_parent sin minWidth`() {
        // Escudo land-TV: réplica exacta (diff solo comentario).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val hero = root.findViewById<View>(R.id.browser_hero)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, hero.layoutParams.width)
        assertEquals(0, hero.minimumWidth)
    }

    @Test
    @Config(qualifiers = "television")
    fun `Y1 S2 tv GONE wrap192 slots inmovil grid al selector`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val hero = root.findViewById<View>(R.id.browser_hero)
        // GONE de serie (cap 160 + interior intactos, ver paridad).
        assertEquals(View.GONE, hero.visibility)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        assertEquals(dp(context, 192), wrap.minimumHeight)
        // Slots inmóviles: 6 celdas a peso sin HSV ni spacer, margen 12.
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        assertEquals(6, slots.childCount)
        assertEquals(R.id.browser_slot_0, slots.getChildAt(0).id)
        assertEquals(
            dp(context, 12),
            (slots.layoutParams as ViewGroup.MarginLayoutParams).topMargin,
        )
        for (i in 0..5) {
            val lp = slots.getChildAt(i).layoutParams as LinearLayout.LayoutParams
            assertEquals(0, lp.width)
            assertEquals(1f, lp.weight, 0.001f)
        }
        // Pegados tras medir y tumbar: slots.bottom == wrap.top.
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1920, 1080)
        assertEquals(slots.bottom, wrap.top)
        // Grid arriba al selector con hero GONE (XML + puro).
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertEquals(R.id.browser_list_selector, grid.nextFocusUpId)
        assertEquals(
            R.id.browser_list_selector,
            ChannelBrowserFragment.heroGridUpTarget(heroVisible = false),
        )
    }

    @Test
    @Config(qualifiers = "television")
    fun `Y1 S2 tv visible grid al VER`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val fragment = ChannelBrowserFragment()
        fragment.applyGridUpTarget(root, heroVisible = true, isTv = true)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertEquals(R.id.browser_hero_play, grid.nextFocusUpId)
        assertEquals(
            R.id.browser_hero_play,
            ChannelBrowserFragment.heroGridUpTarget(heroVisible = true),
        )
        // El VER conserva sus salidas.
        val play = root.findViewById<View>(R.id.browser_hero_play)
        assertEquals(R.id.browser_grid, play.nextFocusDownId)
        assertEquals(R.id.browser_sidebar, play.nextFocusLeftId)
        assertEquals(R.id.browser_list_selector, play.nextFocusUpId)
    }

    @Test
    @Config(qualifiers = "land-television")
    fun `Y1 S2 escudo GONE wrap192 y visible al VER`() {
        // Escudo land-TV: réplica exacta (diff solo comentario).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val hero = root.findViewById<View>(R.id.browser_hero)
        assertEquals(View.GONE, hero.visibility)
        assertEquals(dp(context, 192), root.findViewById<View>(R.id.browser_grid_wrap).minimumHeight)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertEquals(R.id.browser_list_selector, grid.nextFocusUpId)
        ChannelBrowserFragment().applyGridUpTarget(root, heroVisible = true, isTv = true)
        assertEquals(R.id.browser_hero_play, grid.nextFocusUpId)
    }
}
