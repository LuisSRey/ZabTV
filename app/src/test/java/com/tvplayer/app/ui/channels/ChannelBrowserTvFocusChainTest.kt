package com.tvplayer.app.ui.channels

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
 * Cadena de foco TV W3 S4 + reskin B1/B2: el wrap porta clip vertical custom
 * (VerticalClipFrameLayout: clip de canvas 0..height + slop lateral; XML sin
 * protect: el attach de un tile abre la cadena entera) y la grid apunta arriba
 * al VER con hero visible o al selector con hero GONE. Solo TV; móvil intacto.
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
    fun `wrap con clip vertical custom en XML y sin protect`() {
        // FRENTE B (S3): el wrap contiene en vertical por clip de canvas y
        // nadie lo protege: el XML deja los flags abiertos y el clip vertical
        // no depende de ellos.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val wrap = root.findViewById<ViewGroup>(R.id.browser_grid_wrap)
        assertFalse(wrap.clipChildren)
        assertFalse(FocusScaleHelper.isProtected(wrap))
    }

    @Test
    fun `wrap con clip vertical custom (attach de un tile abre la cadena entera)`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = LayoutInflater.from(activity)
            .inflate(R.layout.fragment_channel_browser, null)
        activity.setContentView(root)
        val wrap = root.findViewById<ViewGroup>(R.id.browser_grid_wrap)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        // Como en bindBrowserViews: la grid tiene manager antes de los tiles.
        grid.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
        val tile = View(activity)
        grid.addView(tile)
        // Clip vertical custom: el attach clásico abre la cadena entera (wrap
        // y raíz con flags abiertos); el halo 1.1 desborda hasta el aire
        // lateral y el recorte vertical lo pone el canvas del wrap.
        FocusScaleHelper.attach(tile)
        assertFalse(wrap.clipChildren)
        assertFalse((root as ViewGroup).clipChildren)
    }
}
