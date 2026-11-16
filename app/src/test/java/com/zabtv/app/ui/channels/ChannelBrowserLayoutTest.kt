package com.zabtv.app.ui.channels

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.R
import com.zabtv.app.core.xmltv.GuideWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fix Canales-móvil D1 (S4): fila móvil con altura fija y celdas fijas —
 * sin match_parent en cadena wrap_content. TV prohibido (sin cambios).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserLayoutTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    @Test
    fun `fila movil raiz 72dp fija`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val root = LayoutInflater.from(context).inflate(R.layout.item_channel, parent, false)
        assertEquals(dp(context, 72), root.layoutParams.height)
    }

    @Test
    fun `celda timeline movil 110x56dp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val cell = LayoutInflater.from(context).inflate(R.layout.item_channel_timeline, parent, false) as TextView
        assertEquals(dp(context, 110), cell.layoutParams.width)
        assertEquals(dp(context, 56), cell.layoutParams.height)
    }

    @Test
    fun `fila movil lleva tira timeline`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val root = LayoutInflater.from(context).inflate(R.layout.item_channel, parent, false)
        val strip = root.findViewById<LinearLayout>(R.id.channel_timeline)
        // La tira existe en móvil (el adapter la protege en TV con null-safe).
        assertEquals(true, strip != null)
    }

    @Test
    fun `browser movil lleva selector dropdown 48dp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = android.widget.FrameLayout(context)
        val root = LayoutInflater.from(context).inflate(R.layout.fragment_channel_browser, parent, false)
        val selector = root.findViewById<TextView>(R.id.browser_list_selector)
        assertEquals(true, selector != null)
        assertEquals(dp(context, 48), selector.layoutParams.height)
    }

    @Test
    fun `rail movil 40dp solo filtros`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = android.widget.FrameLayout(context)
        val root = LayoutInflater.from(context).inflate(R.layout.fragment_channel_browser, parent, false)
        val sidebar = root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.browser_sidebar)
        assertEquals(dp(context, 40), sidebar.layoutParams.height)
    }

    @Test
    fun `resto de ids del browser intactos`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = android.widget.FrameLayout(context)
        val root = LayoutInflater.from(context).inflate(R.layout.fragment_channel_browser, parent, false)
        assertEquals(true, root.findViewById<android.view.View>(R.id.browser_hero) != null)
        assertEquals(true, root.findViewById<View>(R.id.browser_slots) != null)
        assertEquals(true, root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.browser_grid) != null)
        assertEquals(true, root.findViewById<TextView>(R.id.browser_empty) != null)
    }

    @Test
    fun `slots portrait 6 cortes de 110dp con spacer offset en HSV`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = android.widget.FrameLayout(context)
        val root = LayoutInflater.from(context).inflate(R.layout.fragment_channel_browser, parent, false)
        // MISMO id que el land: LinearLayout horizontal dentro del HSV.
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        assertTrue(slots != null)
        assertEquals(LinearLayout.HORIZONTAL, slots.orientation)
        assertTrue(slots.parent is HorizontalScrollView)
        // marginTop 12dp del HSV conservado (el land usa 8dp).
        val hsv = slots.parent as View
        assertEquals(
            dp(context, 12),
            (hsv.layoutParams as ViewGroup.MarginLayoutParams).topMargin,
        )
        // Spacer (= offset del timeline) + 6 cortes.
        assertEquals(7, slots.childCount)
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.browser_timeline_offset),
            (slots.getChildAt(0).layoutParams as LinearLayout.LayoutParams).width,
        )
        val ids = listOf(
            R.id.browser_slot_0, R.id.browser_slot_1, R.id.browser_slot_2,
            R.id.browser_slot_3, R.id.browser_slot_4, R.id.browser_slot_5,
        )
        val expectedSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 12f, context.resources.displayMetrics,
        )
        for (i in 0..5) {
            val cell = slots.getChildAt(i + 1) as TextView
            assertEquals(ids[i], cell.id)
            assertEquals(dp(context, 110), cell.layoutParams.width)
            assertEquals(expectedSize, cell.textSize, 1.0f)
            assertEquals(context.getColor(R.color.tp_text_secondary), cell.currentTextColor)
            assertEquals(1, cell.maxLines)
            assertEquals(android.text.TextUtils.TruncateAt.END, cell.ellipsize)
        }
        // El hero no contiene la cabecera: van de hermanos.
        var p: ViewParent? = slots.parent
        while (p != null) {
            assertTrue((p as? View)?.id != R.id.browser_hero)
            p = p.parent
        }
    }

    @Test
    fun `tira movil con fading horizontal 12dp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val root = LayoutInflater.from(context).inflate(R.layout.item_channel, parent, false) as LinearLayout
        val hsv = (0 until root.childCount).map { root.getChildAt(it) }
            .filterIsInstance<android.widget.HorizontalScrollView>().single()
        assertTrue(hsv.isHorizontalFadingEdgeEnabled)
        assertEquals(dp(context, 12), hsv.getHorizontalFadingEdgeLength())
        assertEquals(false, hsv.isVerticalFadingEdgeEnabled)
    }

    @Test
    fun `reintento timeline acotado 200ms max 1`() {
        assertEquals(1, ChannelBrowserFragment.TIMELINE_MAX_RETRY)
        assertEquals(200L, ChannelBrowserFragment.TIMELINE_RETRY_DELAY_MS)
    }

    @Test
    fun `slotsLabels 6 cortes HH-mm puros`() {
        val anchor = GuideWindow.anchorFor(1_794_294_600_000L)
        val window = GuideWindow(anchor)
        val labels = ChannelBrowserFragment.slotsLabels(window)
        assertEquals(6, labels.size)
        for (label in labels) {
            assertTrue(label.matches(Regex("\\d{2}:\\d{2}")))
        }
    }

    @Test
    fun `slotsText une slotsLabels con punto medio`() {
        val anchor = GuideWindow.anchorFor(1_794_294_600_000L)
        val window = GuideWindow(anchor)
        assertEquals(
            ChannelBrowserFragment.slotsLabels(window).joinToString("  ·  "),
            ChannelBrowserFragment.slotsText(window),
        )
    }

    @Test
    fun `slots 6 cortes HH-mm misma ventana`() {
        val anchor = com.zabtv.app.core.xmltv.GuideWindow.anchorFor(1_794_294_600_000L)
        val window = com.zabtv.app.core.xmltv.GuideWindow(anchor)
        val text = ChannelBrowserFragment.slotsText(window)
        val parts = text.split("  ·  ")
        assertEquals(6, parts.size)
        for (part in parts) {
            assertTrue(part.matches(Regex("\\d{2}:\\d{2}")))
        }
    }
}
