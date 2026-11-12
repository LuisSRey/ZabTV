package com.tvplayer.app.ui.channels

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Z3 verificación geométrica (TV+escudo, sin tocar layouts): la deriva
 * slots-vs-timeline se explica solo por insets conocidos —
 * wrap 28dp = slots 14dp + item (margen 6dp + padding 8dp) — y queda como
 * follow-up sin tocar si supera la tolerancia. Prohibido spacer
 * compensatorio (también en el escudo).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserZ3AlignTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun tvRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
    }

    /** Insets del item TV (margen + padding) leídos del layout, no a mano. */
    private fun itemInsetsPx(context: Context): Int {
        val parent = FrameLayout(context)
        val item = LayoutInflater.from(context)
            .inflate(R.layout.item_channel, parent, false) as LinearLayout
        val lp = item.layoutParams as ViewGroup.MarginLayoutParams
        return lp.leftMargin + lp.rightMargin + item.paddingStart + item.paddingEnd
    }

    @Test
    @Config(qualifiers = "television")
    fun `Z3 tv insets wrap28 slots14 item14 explican deriva sin spacer`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        // Relación geométrica Z1+Z2: wrap(28) == slots(14) + item(6+8).
        assertEquals(dp(context, 28), wrap.paddingStart)
        assertEquals(dp(context, 14), slots.paddingStart)
        assertEquals(dp(context, 6 + 8) * 2, itemInsetsPx(context))
        assertEquals(
            wrap.paddingStart + wrap.paddingEnd,
            slots.paddingStart + slots.paddingEnd + itemInsetsPx(context),
        )
        // Sin spacer ni HSV por ids.
        assertEquals(6, slots.childCount)
        assertEquals(R.id.browser_slot_0, slots.getChildAt(0).id)
        assertTrue(slots.parent !is HorizontalScrollView)
    }

    @Test
    @Config(qualifiers = "television")
    fun `Z3 tv deriva medida dentro de tolerancia de insets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1920, 1080)
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        // Misma columna: anchos relativos iguales por ids.
        assertEquals(wrap.width, slots.width)
        // Celdas del header reparten igual entre sí (deriva interna ≤1px).
        val widths = (0 until slots.childCount).map { slots.getChildAt(it).width }
        assertEquals(6, widths.size)
        assertTrue(widths.all { it > 0 })
        assertTrue("celdas slots desiguales: $widths", widths.max() - widths.min() <= 1)
        // Deriva total contra la fila = 2*wrap (56dp): slotsContent(W-28) vs
        // timeline(W-84). Tolerancia ±2px por redondeo de pesos; si supera,
        // FOLLOW-UP sin tocar (prohibido spacer compensatorio).
        val slotsContent = slots.width - slots.paddingStart - slots.paddingEnd
        val wrapContent = wrap.width - wrap.paddingStart - wrap.paddingEnd
        val timelineWidth = wrapContent - itemInsetsPx(context)
        val drift = slotsContent - timelineWidth
        assertEquals(
            "deriva $drift px fuera de tolerancia de insets: FOLLOW-UP sin tocar, prohibido spacer",
            dp(context, 56).toFloat(),
            drift.toFloat(),
            2f,
        )
    }

    @Test
    @Config(qualifiers = "land-television")
    fun `Z3 escudo insets y deriva replican TV sin spacer`() {
        // Escudo land-TV: misma geometría (diff solo comentario).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1920, 1080)
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        assertEquals(dp(context, 28), wrap.paddingStart)
        assertEquals(dp(context, 14), slots.paddingStart)
        assertEquals(
            wrap.paddingStart + wrap.paddingEnd,
            slots.paddingStart + slots.paddingEnd + itemInsetsPx(context),
        )
        assertEquals(6, slots.childCount)
        assertEquals(R.id.browser_slot_0, slots.getChildAt(0).id)
        assertTrue(slots.parent !is HorizontalScrollView)
        assertEquals(wrap.width, slots.width)
        val widths = (0 until slots.childCount).map { slots.getChildAt(it).width }
        assertTrue(widths.all { it > 0 })
        assertTrue("celdas slots desiguales: $widths", widths.max() - widths.min() <= 1)
        val drift = (slots.width - slots.paddingStart - slots.paddingEnd) -
            ((wrap.width - wrap.paddingStart - wrap.paddingEnd) - itemInsetsPx(context))
        assertEquals(
            "deriva $drift px fuera de tolerancia de insets: FOLLOW-UP sin tocar, prohibido spacer",
            dp(context, 56).toFloat(),
            drift.toFloat(),
            2f,
        )
    }
}
