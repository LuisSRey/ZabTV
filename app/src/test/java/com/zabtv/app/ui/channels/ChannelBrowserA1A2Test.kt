package com.zabtv.app.ui.channels

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A1/A2 TV (S1+S2) + B1/B2: la fila TV mueve el fondo al tile interno
 * (raíz transparente sin margen, aire en el inner 16h/8v) y el browser
 * alinea slots (36dp = wrap 20dp + inner 16dp) con el tile. Solo
 * layout-television (+ réplica land-television); móvil intacto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserA1A2Test {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun itemRoot(context: Context): LinearLayout {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.item_channel, parent, false) as LinearLayout
    }

    // ---- S1: item TV ----

    @Test
    @Config(qualifiers = "television")
    fun `S1 tv raiz transparente sin margen con aire vertical 6dp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = itemRoot(context)
        assertEquals(LinearLayout.VERTICAL, root.orientation)
        assertTrue(root.isFocusable)
        // Fondo transparente (el tile_focus_bg baja al inner).
        val bg = root.background as? ColorDrawable
        assertNotNull(bg)
        assertEquals(android.graphics.Color.TRANSPARENT, bg?.color)
        // Sin margen; aire vertical en la raíz, lateral en el inner.
        val lp = root.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals(0, lp.leftMargin)
        assertEquals(0, lp.rightMargin)
        assertEquals(0, root.paddingStart)
        assertEquals(0, root.paddingEnd)
        assertEquals(dp(context, 6), root.paddingTop)
        assertEquals(dp(context, 6), root.paddingBottom)
    }

    @Test
    @Config(qualifiers = "television")
    fun `S1 tv inner con fondo tile y aire 16h 8v no focable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = itemRoot(context)
        val inner = root.findViewById<LinearLayout>(R.id.channel_tile_inner)
        assertNotNull(inner)
        assertEquals(root, inner.parent as View)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, inner.layoutParams.width)
        assertEquals(LinearLayout.VERTICAL, inner.orientation)
        assertFalse(inner.isFocusable)
        // Fondo con forma (selector, no color plano) + aire 16h/8v.
        assertTrue(inner.background != null)
        assertTrue(inner.background !is ColorDrawable)
        assertEquals(dp(context, 16), inner.paddingStart)
        assertEquals(dp(context, 16), inner.paddingEnd)
        assertEquals(dp(context, 8), inner.paddingTop)
        assertEquals(dp(context, 8), inner.paddingBottom)
    }

    @Test
    @Config(qualifiers = "television")
    fun `S1 tv cabecera y timeline intactos dentro del inner`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = itemRoot(context)
        val inner = root.findViewById<LinearLayout>(R.id.channel_tile_inner)
        // La raíz solo porta el inner.
        assertEquals(1, root.childCount)
        // Cabecera: horizontal con nº + logo 96x56 + nombre/tira.
        val header = inner.getChildAt(0) as LinearLayout
        assertEquals(LinearLayout.HORIZONTAL, header.orientation)
        assertTrue(header.findViewById<View>(R.id.channel_number) != null)
        val logo = header.findViewById<View>(R.id.channel_logo)
        assertEquals(dp(context, 96), logo.layoutParams.width)
        assertEquals(dp(context, 56), logo.layoutParams.height)
        assertTrue(header.findViewById<View>(R.id.channel_name) != null)
        assertTrue(header.findViewById<View>(R.id.channel_epg) != null)
        // Timeline intacto: segundo hijo, match_parent horizontal, marginTop 8.
        val strip = inner.findViewById<LinearLayout>(R.id.channel_timeline)
        assertNotNull(strip)
        assertEquals(inner, strip.parent as View)
        assertEquals(1, inner.indexOfChild(strip))
        assertEquals(LinearLayout.HORIZONTAL, strip.orientation)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, strip.layoutParams.width)
        assertEquals(dp(context, 8), (strip.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
    }

    @Test
    fun `S1 movil intacto sin inner`() {        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = itemRoot(context)
        // Móvil: raíz 3dp con fondo tile y padding 6, sin inner.
        val lp = root.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals(dp(context, 3), lp.leftMargin)
        assertEquals(dp(context, 6), root.paddingStart)
        assertTrue(root.background != null)
        assertTrue(root.background !is ColorDrawable)
        assertNull(root.findViewById<View>(R.id.channel_tile_inner))
        assertTrue(root.findViewById<View>(R.id.channel_number) != null)
        assertTrue(root.findViewById<View>(R.id.channel_timeline) != null)
    }

    // ---- S2: browser TV + escudo ----

    private fun browserRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
    }

    @Test
    @Config(qualifiers = "television")
    fun `S2 tv wrap aire lateral 20dp resto matriz intacta`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = browserRoot(context)
        val wrap = root.findViewById<ViewGroup>(R.id.browser_grid_wrap)
        // B2: aire lateral 20dp (los slots suman 20+16=36).
        assertEquals(dp(context, 20), wrap.paddingStart)
        assertEquals(dp(context, 20), wrap.paddingEnd)
        // Resto matriz intacta (clip vertical custom + halo + peso + minHeight).
        assertFalse(wrap.clipChildren)
        assertFalse(wrap.clipToPadding)
        assertEquals(0, wrap.paddingTop)
        assertEquals(0, wrap.paddingBottom)
        assertEquals(0, (wrap.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        val wlp = wrap.layoutParams as LinearLayout.LayoutParams
        assertEquals(1f, wlp.weight, 0.001f)
        assertEquals(dp(context, 192), wrap.minimumHeight)
        assertFalse(wrap.isFocusable)
        val grid = root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.browser_grid)
        assertEquals(wrap, grid.parent as View)
        assertFalse((grid as ViewGroup).clipChildren)
        assertFalse(grid.clipToPadding)
        assertEquals(dp(context, 12), grid.paddingTop)
        assertEquals(0, (grid.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
    }

    @Test
    @Config(qualifiers = "television")
    fun `S2 tv slots aire lateral 36dp celdas intactas`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = browserRoot(context)
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        // B2: 36dp = wrap 20dp + inner 16dp (alineado con el tile).
        assertEquals(dp(context, 36), slots.paddingStart)
        assertEquals(dp(context, 36), slots.paddingEnd)
        // Celdas intactas: 6 a peso, sin spacer ni HSV.
        assertEquals(6, slots.childCount)
        assertEquals(R.id.browser_slot_0, slots.getChildAt(0).id)
        assertTrue(slots.parent !is android.widget.HorizontalScrollView)
        assertEquals(dp(context, 2), slots.paddingTop)
        assertEquals(dp(context, 2), slots.paddingBottom)
        for (i in 0..5) {
            val lp = slots.getChildAt(i).layoutParams as LinearLayout.LayoutParams
            assertEquals(0, lp.width)
            assertEquals(1f, lp.weight, 0.001f)
        }
    }

    @Test
    @Config(qualifiers = "land-television")
    fun `S2 escudo replica wrap 20 y slots 36`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = browserRoot(context)
        val wrap = root.findViewById<ViewGroup>(R.id.browser_grid_wrap)
        assertEquals(dp(context, 20), wrap.paddingStart)
        assertEquals(dp(context, 20), wrap.paddingEnd)
        assertFalse(wrap.clipChildren)
        assertFalse(wrap.clipToPadding)
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        assertEquals(dp(context, 36), slots.paddingStart)
        assertEquals(dp(context, 36), slots.paddingEnd)
        assertEquals(6, slots.childCount)
        assertEquals(R.id.browser_slot_0, slots.getChildAt(0).id)
        assertTrue(slots.parent !is android.widget.HorizontalScrollView)
    }

    // ---- S4: adapter ----

    private fun tvHolder(context: Context): ChannelGridAdapter.Holder {
        val adapter = ChannelGridAdapter(onClick = {})
        val parent = androidx.recyclerview.widget.RecyclerView(context)
        parent.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        return adapter.onCreateViewHolder(parent, 0)
    }

    private fun idleMain() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(300))
    }

    @Test
    @Config(qualifiers = "television")
    fun `S4 tv holder cablea attach en raiz anima raiz inner quieto`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val holder = tvHolder(context)
        val inner = holder.inner
        assertNotNull(inner)
        // B1: attach clásico en la raíz (unificado TV/móvil): la raíz
        // escala, el inner no.
        assertTrue(holder.itemView.isFocusable)
        assertNotNull(holder.itemView.onFocusChangeListener)
        assertNull(inner?.onFocusChangeListener)
        holder.itemView.onFocusChangeListener.onFocusChange(holder.itemView, true)
        idleMain()
        assertEquals(1.1f, holder.itemView.scaleX, 0.001f)
        assertEquals(1.1f, holder.itemView.scaleY, 0.001f)
        assertEquals(1.0f, inner!!.scaleX, 0.001f)
        assertEquals(1.0f, inner.scaleY, 0.001f)
        // Sin inner: el logo sigue cableado (celdas intactas).
        assertEquals(6, holder.timeline?.childCount)
    }

    @Test
    @Config(qualifiers = "television")
    fun `S4 tv onViewRecycled resetea inner y limpia logo`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = ChannelGridAdapter(onClick = {})
        val holder = tvHolder(context)
        val inner = holder.inner
        assertNotNull(inner)
        inner!!.scaleX = 1.1f
        inner.scaleY = 1.1f
        inner.translationZ = 8f
        holder.logo.tag = "https://s/rancio.png"
        adapter.onViewRecycled(holder)
        assertEquals(1.0f, inner.scaleX, 0.001f)
        assertEquals(1.0f, inner.scaleY, 0.001f)
        assertEquals(0f, inner.translationZ, 0.001f)
        assertNull(holder.logo.tag)
    }

    @Test
    @Config(qualifiers = "television")
    fun `S4 tv raiz focable e inner no focable click en raiz`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val holder = tvHolder(context)
        // La raíz porta foco/click; el inner solo anima.
        assertTrue(holder.itemView.isFocusable)
        assertFalse(holder.inner?.isFocusable ?: true)
    }

    @Test
    fun `S4 movil sin inner reciclado seguro`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = ChannelGridAdapter(onClick = {})
        val parent = androidx.recyclerview.widget.RecyclerView(context)
        parent.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        assertNull(holder.inner)
        // Reciclado sin inner: no crashea y limpia el logo.
        holder.logo.tag = "https://s/rancio.png"
        adapter.onViewRecycled(holder)
        assertNull(holder.logo.tag)
    }
}
