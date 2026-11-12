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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contención grid TV (S1-S6): el wrap `browser_grid_wrap` porta el peso 1 y
 * el minHeight 192dp; la grid vive dentro en match_parent sin peso ni
 * minHeight. Hero arriba (margin 0) con elevation 16, slots opacos con
 * elevation 12, halo con paddingTop 12 + clipToPadding true. Solo TV +
 * escudo land-television; móvil intacto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "television")
class ChannelBrowserGridContainTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun tvRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
    }

    @Test
    fun `S1 wrap porta peso1 y minHeight192 con grid dentro en match_parent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        assertNotNull(wrap)
        val wlp = wrap.layoutParams as LinearLayout.LayoutParams
        assertEquals(1f, wlp.weight, 0.001f)
        assertEquals(0, wlp.height)
        assertEquals(dp(context, 192), wrap.minimumHeight)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertEquals(wrap, grid.parent as View)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, grid.layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, grid.layoutParams.height)
        assertEquals(0, grid.minimumHeight)
    }

    @Test
    fun `S1 grid sin peso ni minHeight`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        val glp = grid.layoutParams
        if (glp is LinearLayout.LayoutParams) {
            assertEquals(0f, glp.weight, 0.001f)
        }
        assertEquals(0, grid.minimumHeight)
    }

    @Test
    fun `S2 hero elevation16 y strip sin fondo con padding 2dp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val hero = root.findViewById<View>(R.id.browser_hero)
        assertEquals(dp(context, 16).toFloat(), hero.elevation, 1f)
        // W2 S2: tira sin fondo ni elevación, solo aire vertical 2dp; resto
        // (6 cortes, marginTop 12) intacto.
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        assertEquals(0f, slots.elevation, 0.1f)
        assertTrue(slots.background == null)
        assertEquals(dp(context, 2), slots.paddingTop)
        assertEquals(dp(context, 2), slots.paddingBottom)
        assertEquals(dp(context, 12), (slots.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        assertEquals(6, slots.childCount)
    }

    @Test
    fun `S3 halo grid clipToPadding true paddingTop12 clipChildren false wrap clips default`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertTrue(grid.clipToPadding)
        assertEquals(dp(context, 12), grid.paddingTop)
        assertFalse((grid as ViewGroup).clipChildren)
        val wrap = root.findViewById<ViewGroup>(R.id.browser_grid_wrap)
        assertTrue(wrap.clipChildren)
        assertTrue(wrap.clipToPadding)
    }

    @Test
    fun `S4 hero arriba marginTop0 y compacto intacto`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val hero = root.findViewById<View>(R.id.browser_hero)
        assertEquals(0, (hero.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        val title = root.findViewById<TextView>(R.id.browser_hero_title)
        val expectedTitle = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 18f, context.resources.displayMetrics,
        )
        assertEquals(expectedTitle, title.textSize, 1.0f)
        assertEquals(1, title.maxLines)
        val now = root.findViewById<TextView>(R.id.browser_hero_now)
        assertEquals(dp(context, 4), (now.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        val progress = root.findViewById<android.widget.ProgressBar>(R.id.browser_hero_progress)
        assertEquals(dp(context, 4), progress.minimumHeight)
        val play = root.findViewById<View>(R.id.browser_hero_play)
        assertEquals(dp(context, 48), play.layoutParams.height)
        assertEquals(160, ChannelBrowserFragment.HERO_MAX_DP_TV)
    }

    @Test
    fun `S5 guardia hero-GONE wrap minHeight y empty hermana`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        assertEquals(dp(context, 192), wrap.minimumHeight)
        val empty = root.findViewById<TextView>(R.id.browser_empty)
        assertEquals(wrap.parent, empty.parent)
        val content = wrap.parent as ViewGroup
        assertTrue(content.indexOfChild(wrap) < content.indexOfChild(empty))
        assertTrue(empty.visibility == View.GONE)
    }

    @Test
    fun `S6 blindaje wrap no focable y nextFocus intacto`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        assertFalse(wrap.isFocusable)
        val selector = root.findViewById<TextView>(R.id.browser_list_selector)
        val sidebar = root.findViewById<RecyclerView>(R.id.browser_sidebar)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        val play = root.findViewById<View>(R.id.browser_hero_play)
        assertEquals(R.id.browser_sidebar, selector.nextFocusDownId)
        assertEquals(R.id.browser_list_selector, sidebar.nextFocusUpId)
        assertEquals(R.id.browser_grid, sidebar.nextFocusRightId)
        assertEquals(R.id.browser_list_selector, grid.nextFocusUpId)
        assertEquals(R.id.browser_sidebar, grid.nextFocusLeftId)
        assertEquals(R.id.browser_grid, play.nextFocusDownId)
    }

    @Test
    @Config(sdk = [34], qualifiers = "land-television")
    fun `escudo land-television replica contencion`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = FrameLayout(context)
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        assertNotNull(wrap)
        assertEquals(dp(context, 192), wrap.minimumHeight)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertEquals(wrap, grid.parent as View)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, grid.layoutParams.height)
        val hero = root.findViewById<View>(R.id.browser_hero)
        assertEquals(0, (hero.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        assertEquals(dp(context, 16).toFloat(), hero.elevation, 1f)
    }
}
