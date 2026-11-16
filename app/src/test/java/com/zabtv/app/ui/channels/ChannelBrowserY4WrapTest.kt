package com.zabtv.app.ui.channels

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Y1 S4 + Z1 + A1/A2 (TV+escudo): el wrap queda sin aire lateral
 * (28→0dp, el lateral vive en los slots 16dp y en el inner del item);
 * el resto de la matriz queda intacto (clip vertical custom en el wrap,
 * pesos, minHeight, halo). Solo layout-television (+ réplica); móvil intacto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserY4WrapTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun tvRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
    }

    @Test
    @Config(qualifiers = "television")
    fun `Y1 S4 tv wrap aire lateral 20dp B2 resto matriz intacta`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val wrap = root.findViewById<ViewGroup>(R.id.browser_grid_wrap)
        assertEquals(dp(context, 20), wrap.paddingStart)
        assertEquals(dp(context, 20), wrap.paddingEnd)
        // Resto matriz X1 S3 intacto: clip vertical custom (el halo desborda).
        assertFalse(wrap.clipChildren)
        assertFalse(wrap.clipToPadding)
        assertEquals(0, wrap.paddingTop)
        assertEquals(0, wrap.paddingBottom)
        assertEquals(0, (wrap.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        val wlp = wrap.layoutParams as LinearLayout.LayoutParams
        assertEquals(1f, wlp.weight, 0.001f)
        assertEquals(dp(context, 192), wrap.minimumHeight)
        assertFalse(wrap.isFocusable)
    }

    @Test
    @Config(qualifiers = "television")
    fun `Y1 S4 tv grid intacta`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        assertEquals(wrap, grid.parent as View)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, grid.layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, grid.layoutParams.height)
        assertEquals(0, grid.minimumHeight)
        assertFalse((grid as ViewGroup).clipChildren)
        assertFalse(grid.clipToPadding)
        assertEquals(dp(context, 12), grid.paddingTop)
        assertEquals(0, (grid.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        assertEquals(R.id.browser_list_selector, grid.nextFocusUpId)
        assertEquals(R.id.browser_sidebar, grid.nextFocusLeftId)
    }

    @Test
    @Config(qualifiers = "land-television")
    fun `Y1 S4 escudo wrap 20dp B2 y grid intacta`() {
        // Escudo land-TV: réplica exacta (diff solo comentario).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val wrap = root.findViewById<ViewGroup>(R.id.browser_grid_wrap)
        assertEquals(dp(context, 20), wrap.paddingStart)
        assertEquals(dp(context, 20), wrap.paddingEnd)
        assertFalse(wrap.clipChildren)
        assertFalse(wrap.clipToPadding)
        assertEquals(0, wrap.paddingTop)
        assertEquals(0, wrap.paddingBottom)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertEquals(wrap, grid.parent as View)
        assertFalse((grid as ViewGroup).clipChildren)
        assertFalse(grid.clipToPadding)
        assertEquals(dp(context, 12), grid.paddingTop)
        assertEquals(0, (grid.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
    }
}
