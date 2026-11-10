package com.tvplayer.app.ui.channels

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Paridad TV D1+D2 (S1): el browser TV lleva el mismo selector de playlist
 * (dropdown 48dp) que el móvil, bajo el título en la columna derecha, con
 * nextFocus explícitos. Resto intacto (safe 56dp, sidebar 260dp).
 * Solo layout-television; layout/ móvil prohibido.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "television")
class ChannelBrowserTvParityTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun tvBrowserRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
    }

    @Test
    fun `S1 tv browser lleva selector dropdown 48dp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvBrowserRoot(context)
        val selector = root.findViewById<TextView>(R.id.browser_list_selector)
        assertTrue(selector != null)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, selector.layoutParams.width)
        assertEquals(dp(context, 48), selector.layoutParams.height)
        assertTrue(selector.isFocusable)
        assertTrue(selector.isClickable)
        assertTrue(selector.background != null)
    }

    @Test
    fun `S1 tv selector texto inicial lists_name 16sp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvBrowserRoot(context)
        val selector = root.findViewById<TextView>(R.id.browser_list_selector)
        assertEquals(context.getString(R.string.lists_name), selector.text.toString())
        val expected = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 16f, context.resources.displayMetrics,
        )
        assertEquals(expected, selector.textSize, 1.0f)
    }

    @Test
    fun `S1 tv resto intacto safe 56dp sidebar 260dp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvBrowserRoot(context)
        assertEquals(dp(context, 56), root.paddingLeft)
        assertEquals(dp(context, 56), root.paddingTop)
        assertEquals(dp(context, 56), root.paddingRight)
        assertEquals(dp(context, 56), root.paddingBottom)
        val sidebar = root.findViewById<RecyclerView>(R.id.browser_sidebar)
        assertEquals(dp(context, 260), sidebar.layoutParams.width)
        assertTrue(root.findViewById<View>(R.id.browser_hero) != null)
        assertTrue(root.findViewById<View>(R.id.browser_hero_play) != null)
        assertTrue(root.findViewById<TextView>(R.id.browser_slots) != null)
        assertTrue(root.findViewById<RecyclerView>(R.id.browser_grid) != null)
        assertTrue(root.findViewById<TextView>(R.id.browser_empty) != null)
    }

    @Test
    fun `S1 tv nextFocus explicitos sin trampas`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvBrowserRoot(context)
        val selector = root.findViewById<TextView>(R.id.browser_list_selector)
        val sidebar = root.findViewById<RecyclerView>(R.id.browser_sidebar)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        val play = root.findViewById<View>(R.id.browser_hero_play)
        assertEquals(R.id.browser_sidebar, selector.nextFocusLeftId)
        assertEquals(R.id.browser_grid, selector.nextFocusDownId)
        assertEquals(R.id.browser_list_selector, sidebar.nextFocusRightId)
        assertEquals(R.id.browser_list_selector, grid.nextFocusUpId)
        assertEquals(R.id.browser_sidebar, grid.nextFocusLeftId)
        assertEquals(R.id.browser_list_selector, play.nextFocusUpId)
        assertEquals(R.id.browser_grid, play.nextFocusDownId)
        // Sin trampas: el selector no se apunta a sí mismo y su salida
        // principal (abajo) es la grilla, siempre visible (el hero puede
        // estar GONE, así que nada lo apunta como único camino).
        assertNotEquals(R.id.browser_list_selector, selector.nextFocusDownId)
    }
}
