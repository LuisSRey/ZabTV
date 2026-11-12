package com.tvplayer.app.ui.channels

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
import com.tvplayer.app.ui.common.FocusScaleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cadena de foco TV W1/W3 S4: el wrap es frontera de clip (protegido +
 * forzado, sobrevive al attach de tiles) y la grid apunta arriba al VER con
 * hero visible o al selector con hero GONE. Solo TV; móvil intacto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "television")
class ChannelBrowserTvFocusChainTest {

    private fun tvRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
    }

    private fun fragment() = ChannelBrowserFragment()

    @Test
    fun `heroGridUpTarget visible lleva al VER y GONE al selector`() {
        assertEquals(
            R.id.browser_hero_play,
            ChannelBrowserFragment.heroGridUpTarget(heroVisible = true),
        )
        assertEquals(
            R.id.browser_list_selector,
            ChannelBrowserFragment.heroGridUpTarget(heroVisible = false),
        )
    }

    @Test
    fun `applyGridUpTarget TV conmuta la grid sin tocar el resto`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        fragment().applyGridUpTarget(root, heroVisible = true, isTv = true)
        assertEquals(R.id.browser_hero_play, grid.nextFocusUpId)
        fragment().applyGridUpTarget(root, heroVisible = false, isTv = true)
        assertEquals(R.id.browser_list_selector, grid.nextFocusUpId)
        // El VER conserva sus salidas.
        val play = root.findViewById<View>(R.id.browser_hero_play)
        assertEquals(R.id.browser_grid, play.nextFocusDownId)
        assertEquals(R.id.browser_sidebar, play.nextFocusLeftId)
        assertEquals(R.id.browser_list_selector, play.nextFocusUpId)
    }

    @Test
    fun `applyGridUpTarget no-TV no toca nada`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        val before = grid.nextFocusUpId
        fragment().applyGridUpTarget(root, heroVisible = true, isTv = false)
        assertEquals(before, grid.nextFocusUpId)
    }

    @Test
    fun `bindClipBoundary TV protege y conserva true-false sin enforce`() {
        // X1 S4 (motivo: el enforce restauraba clipToPadding true y rompía
        // la matriz X1 S3): solo protect — conserva los clips del XML y
        // protege aunque vengan forzados.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        wrap as android.view.ViewGroup
        assertTrue(wrap.clipChildren)
        assertFalse(wrap.clipToPadding)
        fragment().bindClipBoundary(root, isTv = true)
        assertTrue(wrap.clipChildren)
        assertFalse(wrap.clipToPadding)
        assertTrue(FocusScaleHelper.isProtected(wrap))
        wrap.clipChildren = false
        fragment().bindClipBoundary(root, isTv = true)
        assertFalse(wrap.clipChildren)
        assertFalse(wrap.clipToPadding)
        assertTrue(FocusScaleHelper.isProtected(wrap))
    }

    @Test
    fun `bindClipBoundary no-TV no toca nada`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        wrap as android.view.ViewGroup
        wrap.clipChildren = false
        wrap.clipToPadding = false
        fragment().bindClipBoundary(root, isTv = false)
        assertFalse(wrap.clipChildren)
        assertFalse(wrap.clipToPadding)
        assertFalse(FocusScaleHelper.isProtected(wrap))
    }

    @Test
    fun `wrap protegido sobrevive al attach de un tile`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = LayoutInflater.from(activity)
            .inflate(R.layout.fragment_channel_browser, null)
        activity.setContentView(root)
        fragment().bindClipBoundary(root, isTv = true)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        // Como en bindBrowserViews: la grid tiene manager antes de los tiles.
        grid.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
        val tile = View(activity)
        grid.addView(tile)
        FocusScaleHelper.attach(tile)
        // Frontera intacta (contención vertical + aire lateral X1 S3),
        // raíz abierta para el halo de foco.
        assertTrue((wrap as android.view.ViewGroup).clipChildren)
        assertFalse(wrap.clipToPadding)
        assertFalse((root as android.view.ViewGroup).clipChildren)
    }
}
