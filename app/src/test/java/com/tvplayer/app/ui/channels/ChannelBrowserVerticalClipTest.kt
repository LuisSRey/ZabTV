package com.tvplayer.app.ui.channels

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
import com.tvplayer.app.ui.common.VerticalClipFrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FRENTE B S2: el wrap `browser_grid_wrap` de TV pasa de `FrameLayout` a
 * [VerticalClipFrameLayout]: contención vertical por clip de canvas
 * (0..height, slop lateral 10000px) con la MISMA matriz de atributos de antes
 * (peso 1, 0dp, minHeight 192, aire 20/0, sin foco, con flags de clip
 * abiertos). El
 * escudo land-television replica la matriz y móvil no monta ningún wrap custom.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "television")
class ChannelBrowserVerticalClipTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun browserRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
    }

    private fun assertClipVerticalMatrix(context: Context, root: View) {
        val wrap = root.findViewById<ViewGroup>(R.id.browser_grid_wrap)
        assertNotNull(wrap)
        assertTrue(wrap is VerticalClipFrameLayout)
        assertFalse(wrap.clipChildren)
        assertFalse(wrap.clipToPadding)
        assertEquals(dp(context, 20), wrap.paddingStart)
        assertEquals(dp(context, 20), wrap.paddingEnd)
        assertEquals(0, wrap.paddingTop)
        assertEquals(0, wrap.paddingBottom)
        assertEquals(dp(context, 192), wrap.minimumHeight)
        assertFalse(wrap.isFocusable)
        val lp = wrap.layoutParams as LinearLayout.LayoutParams
        assertEquals(1f, lp.weight, 0.001f)
        assertEquals(0, lp.height)
        assertEquals(0, lp.topMargin)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertEquals(wrap, grid.parent as View)
    }

    @Test
    fun `B1 tv wrap es clip vertical con matriz intacta`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertClipVerticalMatrix(context, browserRoot(context))
    }

    @Test
    @Config(sdk = [34], qualifiers = "land-television")
    fun `B1 escudo land-television replica clip vertical`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertClipVerticalMatrix(context, browserRoot(context))
    }

    @Test
    fun `B1 medido 1920x1080 slots pegados arriba y safe area abajo`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = browserRoot(context)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1920, 1080)
        val slots = root.findViewById<View>(R.id.browser_slots)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        // Sin hueco arriba (slots.bottom == wrap.top; mismo padre) y contención
        // antes de la safe area inferior de 56dp (en coordenadas de pantalla:
        // bottom es relativo al padre, de ahí el paseo de offsets).
        assertEquals(slots.bottom, wrap.top)
        assertEquals(1080 - dp(context, 56), absoluteBottom(wrap))
    }

    private fun absoluteBottom(view: View): Int {
        var bottom = view.bottom
        var parent = view.parent
        while (parent is View) {
            bottom += parent.top
            parent = parent.parent
        }
        return bottom
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-port")
    fun `B1 movil sin wrap custom`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = browserRoot(context)
        assertEquals(0, countVerticalClips(root))
    }

    private fun countVerticalClips(view: View): Int {
        var count = if (view is VerticalClipFrameLayout) 1 else 0
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                count += countVerticalClips(view.getChildAt(i))
            }
        }
        return count
    }
}
