package com.zabtv.app.ui.channels

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Z2 (TV+escudo): la cabecera lleva 6 celdas a peso sin spacer ni HSV y con
 * padding lateral 16dp (A1/A2: 14→16, alineado con el inner del item).
 * Solo layout-television (+ réplica); móvil intacto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserZ2SlotsTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun tvRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
    }

    @Test
    @Config(qualifiers = "television")
    fun `Z2 tv slots 6 celdas a peso sin spacer ni HSV padding lateral 36dp B2`() {
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
        // Padding lateral 36dp B2 = wrap 20dp + inner 16dp (aire vertical 2dp W2 S2 intacto).
        assertEquals(dp(context, 36), slots.paddingStart)
        assertEquals(dp(context, 36), slots.paddingEnd)
        assertEquals(dp(context, 2), slots.paddingTop)
        assertEquals(dp(context, 2), slots.paddingBottom)
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
    fun `Z2 escudo slots 6 celdas a peso sin spacer ni HSV padding 36dp B2`() {
        // Escudo land-TV: réplica exacta (diff solo comentario).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        assertEquals(6, slots.childCount)
        assertEquals(R.id.browser_slot_0, slots.getChildAt(0).id)
        assertTrue(slots.parent !is HorizontalScrollView)
        assertEquals(dp(context, 36), slots.paddingStart)
        assertEquals(dp(context, 36), slots.paddingEnd)
        for (i in 0..5) {
            val lp = slots.getChildAt(i).layoutParams as LinearLayout.LayoutParams
            assertEquals(0, lp.width)
            assertEquals(1f, lp.weight, 0.001f)
        }
    }
}
