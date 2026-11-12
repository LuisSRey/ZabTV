package com.tvplayer.app.ui.channels

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
        // W2 S2: tira sin fondo ni elevación, solo aire vertical 2dp;
        // resto (6 cortes, marginTop 12) intacto. X1 S3 (motivo: el aire
        // vertical vive en el paddingTop 12 de la grid, la grid ya no
        // lleva margen y pega slots.bottom == wrap.top).
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        assertEquals(0f, slots.elevation, 0.1f)
        assertTrue(slots.background == null)
        assertEquals(dp(context, 2), slots.paddingTop)
        assertEquals(dp(context, 2), slots.paddingBottom)
        assertEquals(dp(context, 12), (slots.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        assertEquals(6, slots.childCount)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertEquals(0, (grid.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
    }

    @Test
    fun `X1 S3 matriz clip wrap aire12 grid sin margen`() {
        // Y1 S4 (motivo: el aire lateral del wrap abre de 12→16dp; el
        // resto de la matriz X1 S3 queda intacto): el wrap porta
        // clipChildren true + clipToPadding false + aire lateral 16 (el
        // vertical lo pone la grid) + marginTop 0 + peso 1 + minHeight 192;
        // la grid vive dentro en match_parent, clipChildren false,
        // clipToPadding false, paddingTop 12, marginTop 0, sin peso.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val wrap = root.findViewById<ViewGroup>(R.id.browser_grid_wrap)
        assertTrue(wrap.clipChildren)
        assertFalse(wrap.clipToPadding)
        assertEquals(dp(context, 16), wrap.paddingStart)
        assertEquals(dp(context, 16), wrap.paddingEnd)
        assertEquals(0, wrap.paddingTop)
        assertEquals(0, wrap.paddingBottom)
        val wlp = wrap.layoutParams as LinearLayout.LayoutParams
        assertEquals(0, wlp.topMargin)
        assertEquals(1f, wlp.weight, 0.001f)
        assertEquals(dp(context, 192), wrap.minimumHeight)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertEquals(wrap, grid.parent as View)
        assertFalse((grid as ViewGroup).clipChildren)
        assertFalse(grid.clipToPadding)
        assertEquals(dp(context, 12), grid.paddingTop)
        assertEquals(0, (grid.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, grid.layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, grid.layoutParams.height)
        assertEquals(0, grid.minimumHeight)
    }

    @Test
    fun `X1 S3 slots pegados al wrap sin hueco`() {
        // X1 S3: sin márgenes entre slots y wrap, el wrap arranca donde
        // acaban los slots (slots.bottom == wrap.top) tras medir y tumbar.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1920, 1080)
        val slots = root.findViewById<View>(R.id.browser_slots)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        assertEquals(slots.bottom, wrap.top)
    }

    @Test
    fun `X1 S3 halo grid sin clip aire12 reemplaza matriz anterior`() {
        // Y1 S4 (motivo: el aire lateral del wrap abre a 16dp; el vertical
        // sigue en el paddingTop 12 de la grid): grid sin clips con
        // paddingTop 12 + marginTop 0; wrap true/false con aire 16.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertFalse(grid.clipToPadding)
        assertEquals(dp(context, 12), grid.paddingTop)
        assertFalse((grid as ViewGroup).clipChildren)
        assertEquals(0, (grid.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        val wrap = root.findViewById<ViewGroup>(R.id.browser_grid_wrap)
        assertTrue(wrap.clipChildren)
        assertFalse(wrap.clipToPadding)
        assertEquals(dp(context, 16), wrap.paddingStart)
        assertEquals(dp(context, 16), wrap.paddingEnd)
    }

    @Test
    fun `S4 hero mini stacked TV con VER intacto`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        // W3 S3: hero mini apilado (ajuste previsto: padding 4 + VER
        // margin 4 tras medir 152dp en Robolectric TV / ~110dp en
        // dispositivo, por encima de ~100dp).
        val hero = root.findViewById<View>(R.id.browser_hero)
        assertEquals(0, (hero.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        assertEquals(dp(context, 4), hero.paddingLeft)
        assertEquals(dp(context, 4), hero.paddingTop)
        val title = root.findViewById<TextView>(R.id.browser_hero_title)
        val expectedTitle = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 16f, context.resources.displayMetrics,
        )
        assertEquals(expectedTitle, title.textSize, 1.0f)
        assertEquals(1, title.maxLines)
        val now = root.findViewById<TextView>(R.id.browser_hero_now)
        val expectedNow = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 12f, context.resources.displayMetrics,
        )
        assertEquals(expectedNow, now.textSize, 1.0f)
        assertEquals(1, now.maxLines)
        assertEquals(dp(context, 2), (now.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        val progress = root.findViewById<android.widget.ProgressBar>(R.id.browser_hero_progress)
        assertEquals(dp(context, 4), (progress.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        assertEquals(dp(context, 4), progress.minimumHeight)
        val play = root.findViewById<Button>(R.id.browser_hero_play)
        assertEquals(dp(context, 4), (play.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        assertEquals(dp(context, 96), play.minWidth)
        assertEquals(dp(context, 48), play.layoutParams.height)
        assertEquals(dp(context, 48), play.minimumHeight)
        // nextFocus del VER intactos.
        assertEquals(R.id.browser_grid, play.nextFocusDownId)
        assertEquals(R.id.browser_sidebar, play.nextFocusLeftId)
        assertEquals(R.id.browser_list_selector, play.nextFocusUpId)
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
