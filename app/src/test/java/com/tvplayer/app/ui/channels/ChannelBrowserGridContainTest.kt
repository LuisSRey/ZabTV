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
    fun `X1 S3 matriz clip wrap aire20 grid sin margen`() {
        // Y1 S4 + Z1 + A1/A2 + B2 + CA3 (motivo: el wrap deja de ser
        // frontera de clip: el halo 1.1 de las filas desborda el aire 20dp;
        // los slots siguen sumando 20+16=36 alineados con el inner del
        // item): el wrap porta clipChildren false + clipToPadding false +
        // aire lateral 20dp (el vertical lo pone la grid) + marginTop 0 +
        // peso 1 + minHeight 192; la grid vive dentro en match_parent,
        // clipChildren false, clipToPadding false, paddingTop 12,
        // marginTop 0, sin peso.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val wrap = root.findViewById<ViewGroup>(R.id.browser_grid_wrap)
        assertFalse(wrap.clipChildren)
        assertFalse(wrap.clipToPadding)
        assertEquals(dp(context, 20), wrap.paddingStart)
        assertEquals(dp(context, 20), wrap.paddingEnd)
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
    fun `CA3 sin colision por numeros`() {
        // Matriz de aire vs desborde 1.1 (0.05×tamaño por lado): vertical
        // fila paddingV 6dp (12dp entre filas) + grid paddingTop 12dp >
        // desborde vertical máx 6.6dp (fila ~132dp de alto); lateral aire
        // wrap 20dp + inner 16+16dp (gap columnas) = 52dp > desborde d1.5
        // 44.8dp/lado (fila ~896dp ancha). Matriz d1.5 ✓ / d2.0 ✓ / d1.0
        // caveat follow-up ~25dp invasión zona filtros (no bloquea).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val item = LayoutInflater.from(context)
            .inflate(R.layout.item_channel, null) as ViewGroup
        // La raíz del item no clipa (el halo desborda la fila).
        assertFalse(item.clipChildren)
        // Vertical: fila 6dp arriba/abajo + grid paddingTop 12dp.
        assertEquals(dp(context, 6), item.paddingTop)
        assertEquals(dp(context, 6), item.paddingBottom)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertEquals(dp(context, 12), grid.paddingTop)
        // Lateral: wrap 20dp + inner 16+16dp.
        val wrap = root.findViewById<ViewGroup>(R.id.browser_grid_wrap)
        assertEquals(dp(context, 20), wrap.paddingStart)
        assertEquals(dp(context, 20), wrap.paddingEnd)
        val inner = item.findViewById<View>(R.id.channel_tile_inner)
        assertEquals(dp(context, 16), inner.paddingStart)
        assertEquals(dp(context, 16), inner.paddingEnd)
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
        // Y1 S4 + Z1 + A1/A2 + B2 + CA3 (motivo: el wrap deja de ser
        // frontera de clip y el aire lateral sigue en el wrap 20dp con el
        // vertical en el paddingTop 12 de la grid): grid sin clips con
        // paddingTop 12 + marginTop 0; wrap false/false con aire lateral
        // 20dp.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertFalse(grid.clipToPadding)
        assertEquals(dp(context, 12), grid.paddingTop)
        assertFalse((grid as ViewGroup).clipChildren)
        assertEquals(0, (grid.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        val wrap = root.findViewById<ViewGroup>(R.id.browser_grid_wrap)
        assertFalse(wrap.clipChildren)
        assertFalse(wrap.clipToPadding)
        assertEquals(dp(context, 20), wrap.paddingStart)
        assertEquals(dp(context, 20), wrap.paddingEnd)
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
