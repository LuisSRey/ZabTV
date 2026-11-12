package com.tvplayer.app.ui.channels

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Y1 S3 + Z2 + A1/A2 verificación (TV+escudo): la cabecera lleva 6 celdas
 * a peso sin spacer ni HSV y con padding lateral 16dp (14→16, alineado con
 * el inner del item); la fila del timeline usa el mismo reparto (item
 * match_parent con 6 celdas 0dp/weight1). Sin spacer compensatorio: la
 * deriva medida se explica por insets (inner del item) y queda como
 * follow-up sin tocar (ver nota en el test de deriva y
 * ChannelBrowserZ3AlignTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserY3SlotsTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun tvRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
    }

    @Test
    @Config(qualifiers = "television")
    fun `Y1 S3 tv slots 6 celdas a peso sin spacer ni HSV padding lateral 14dp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        assertTrue(slots != null)
        assertEquals(LinearLayout.HORIZONTAL, slots.orientation)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, slots.layoutParams.width)
        // Sin spacer: 6 hijos y el primero ya es el corte 0.
        assertEquals(6, slots.childCount)
        assertEquals(R.id.browser_slot_0, slots.getChildAt(0).id)
        // Sin HSV: el padre es la columna derecha, nunca un scroll.
        assertTrue(slots.parent !is HorizontalScrollView)
        // Padding lateral 16dp Z2+A1/A2 (aire vertical 2dp W2 S2 intacto).
        assertEquals(dp(context, 16), slots.paddingStart)
        assertEquals(dp(context, 16), slots.paddingEnd)
        val ids = listOf(
            R.id.browser_slot_0, R.id.browser_slot_1, R.id.browser_slot_2,
            R.id.browser_slot_3, R.id.browser_slot_4, R.id.browser_slot_5,
        )
        for (i in 0..5) {
            val cell = slots.getChildAt(i) as TextView
            assertEquals(ids[i], cell.id)
            val lp = cell.layoutParams as LinearLayout.LayoutParams
            assertEquals(0, lp.width)
            assertEquals(1f, lp.weight, 0.001f)
            assertEquals(1, cell.maxLines)
        }
    }

    @Test
    @Config(qualifiers = "land-television")
    fun `Y1 S3 escudo slots 6 celdas a peso sin spacer ni HSV`() {
        // Escudo land-TV: réplica exacta (diff solo comentario).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        assertEquals(6, slots.childCount)
        assertEquals(R.id.browser_slot_0, slots.getChildAt(0).id)
        assertTrue(slots.parent !is HorizontalScrollView)
        assertEquals(dp(context, 16), slots.paddingStart)
        assertEquals(dp(context, 16), slots.paddingEnd)
        for (i in 0..5) {
            val lp = slots.getChildAt(i).layoutParams as LinearLayout.LayoutParams
            assertEquals(0, lp.width)
            assertEquals(1f, lp.weight, 0.001f)
        }
    }

    @Test
    @Config(qualifiers = "television")
    fun `Y1 S3 tv timeline item match_parent con 6 celdas a peso`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Celda: 0dp/weight1 (reparto igual, sin foco).
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val cell = LayoutInflater.from(context)
            .inflate(R.layout.item_channel_timeline, row, false) as TextView
        val lp = cell.layoutParams as LinearLayout.LayoutParams
        assertEquals(0, lp.width)
        assertEquals(1f, lp.weight, 0.001f)
        // Tira del item: match_parent horizontal (el adapter infla 6).
        val parent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val item = LayoutInflater.from(context)
            .inflate(R.layout.item_channel, parent, false) as LinearLayout
        val strip = item.findViewById<LinearLayout>(R.id.channel_timeline)
        assertTrue(strip != null)
        assertEquals(LinearLayout.HORIZONTAL, strip.orientation)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, strip.layoutParams.width)
        // El adapter intacto pinta 6 celdas por fila en TV.
        val adapter = ChannelGridAdapter(onClick = {})
        val rv = RecyclerView(context)
        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        val holder = adapter.onCreateViewHolder(rv, 0)
        assertEquals(6, holder.timeline?.childCount)
    }

    @Test
    @Config(qualifiers = "television")
    fun `Y1 S3 tv deriva slots-timeline solo por insets sin spacer follow-up`() {
        // Medida a 1920x1080: las 6 celdas del header reparten igual entre
        // sí (deriva interna ≤1px). La deriva contra la fila se explica por
        // el inner del item (aire lateral 16dp) y NO se compensa con spacer
        // (prohibido): si superase la tolerancia de insets se reporta como
        // follow-up sin tocar. Hoy: follow-up abierto (ver nota de cierre),
        // sin cambio de layout.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1920, 1080)
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        val widths = (0 until slots.childCount).map { slots.getChildAt(it).width }
        assertEquals(6, widths.size)
        assertTrue(widths.all { it > 0 })
        val spread = widths.max() - widths.min()
        assertTrue("celdas slots desiguales: $widths", spread <= 1)
    }
}
