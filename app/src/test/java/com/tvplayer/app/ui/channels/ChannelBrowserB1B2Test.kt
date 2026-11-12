package com.tvplayer.app.ui.channels

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
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
 * B1/B2 TV: halo sin recorte (la raíz del item no clipa: el foco 1.1
 * desborda sin cortarse) + alineación slots 36dp = wrap 20dp + inner
 * 16dp; adapter unificado con attach en la raíz (TV y móvil igual).
 * Solo layout-television (+ réplica land-television); móvil intacto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserB1B2Test {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun itemRoot(context: Context): LinearLayout {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.item_channel, parent, false) as LinearLayout
    }

    private fun browserRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
    }

    private fun idleMain() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(300))
    }

    // ---- B1: item TV sin clip ----

    @Test
    @Config(qualifiers = "television")
    fun `B1 tv item raiz sin clip resto intacto`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = itemRoot(context)
        // B1: la raíz no clipa (el halo 1.1 desborda sin cortarse).
        assertFalse(root.clipChildren)
        // Resto intacto: sin margen, aire vertical 6dp, transparente,
        // focable; el inner porta el tile con aire 16h/8v, no focable.
        val lp = root.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals(0, lp.leftMargin)
        assertEquals(0, lp.rightMargin)
        assertEquals(0, root.paddingStart)
        assertEquals(0, root.paddingEnd)
        assertEquals(dp(context, 6), root.paddingTop)
        assertEquals(dp(context, 6), root.paddingBottom)
        val bg = root.background as? ColorDrawable
        assertNotNull(bg)
        assertEquals(android.graphics.Color.TRANSPARENT, bg?.color)
        assertTrue(root.isFocusable)
        val inner = root.findViewById<LinearLayout>(R.id.channel_tile_inner)
        assertNotNull(inner)
        assertFalse(inner.isFocusable)
        assertEquals(dp(context, 16), inner.paddingStart)
        assertEquals(dp(context, 16), inner.paddingEnd)
        assertEquals(dp(context, 8), inner.paddingTop)
        assertEquals(dp(context, 8), inner.paddingBottom)
        assertEquals(1, root.childCount)
    }

    @Test
    fun `B1 movil intacto sin inner`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = itemRoot(context)
        val lp = root.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals(dp(context, 3), lp.leftMargin)
        assertEquals(dp(context, 6), root.paddingStart)
        assertTrue(root.background != null)
        assertTrue(root.background !is ColorDrawable)
        assertNull(root.findViewById<View>(R.id.channel_tile_inner))
    }

    // ---- B2: browser TV + escudo ----

    @Test
    @Config(qualifiers = "television")
    fun `B2 tv wrap aire 20 resto intacto`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = browserRoot(context)
        val wrap = root.findViewById<ViewGroup>(R.id.browser_grid_wrap)
        // B2: aire lateral 20dp (los slots suman 20+16=36).
        assertEquals(dp(context, 20), wrap.paddingStart)
        assertEquals(dp(context, 20), wrap.paddingEnd)
        // Resto intacto: contención + halo + peso + minHeight.
        assertTrue(wrap.clipChildren)
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
    fun `B2 tv slots aire 36 celdas intactas`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = browserRoot(context)
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        // B2: 36dp = wrap 20dp + inner 16dp (alineado con el tile).
        assertEquals(dp(context, 36), slots.paddingStart)
        assertEquals(dp(context, 36), slots.paddingEnd)
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
    fun `B2 escudo replica wrap 20 y slots 36`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = browserRoot(context)
        val wrap = root.findViewById<ViewGroup>(R.id.browser_grid_wrap)
        assertEquals(dp(context, 20), wrap.paddingStart)
        assertEquals(dp(context, 20), wrap.paddingEnd)
        assertTrue(wrap.clipChildren)
        assertFalse(wrap.clipToPadding)
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        assertEquals(dp(context, 36), slots.paddingStart)
        assertEquals(dp(context, 36), slots.paddingEnd)
        assertEquals(6, slots.childCount)
        assertEquals(R.id.browser_slot_0, slots.getChildAt(0).id)
        assertTrue(slots.parent !is android.widget.HorizontalScrollView)
    }

    // ---- B1: adapter unificado ----

    private fun tvHolder(context: Context): ChannelGridAdapter.Holder {
        val adapter = ChannelGridAdapter(onClick = {})
        val parent = androidx.recyclerview.widget.RecyclerView(context)
        parent.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        return adapter.onCreateViewHolder(parent, 0)
    }

    @Test
    @Config(qualifiers = "television")
    fun `B1 tv holder attach en raiz inner sin animacion`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val holder = tvHolder(context)
        val inner = holder.inner
        assertNotNull(inner)
        // Foco en la raíz con attach clásico: la raíz anima, el inner no.
        assertTrue(holder.itemView.isFocusable)
        assertNotNull(holder.itemView.onFocusChangeListener)
        assertNull(inner?.onFocusChangeListener)
        holder.itemView.onFocusChangeListener.onFocusChange(holder.itemView, true)
        idleMain()
        assertEquals(1.1f, holder.itemView.scaleX, 0.001f)
        assertEquals(1.1f, holder.itemView.scaleY, 0.001f)
        assertEquals(1.0f, inner!!.scaleX, 0.001f)
        assertEquals(1.0f, inner.scaleY, 0.001f)
        holder.itemView.onFocusChangeListener.onFocusChange(holder.itemView, false)
        idleMain()
        assertEquals(1.0f, holder.itemView.scaleX, 0.001f)
        assertEquals(0f, holder.itemView.translationZ, 0.001f)
        assertEquals(6, holder.timeline?.childCount)
    }

    @Test
    @Config(qualifiers = "television")
    fun `B1 tv onViewRecycled resetea itemView y limpia logo`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = ChannelGridAdapter(onClick = {})
        val holder = tvHolder(context)
        holder.itemView.scaleX = 1.1f
        holder.itemView.scaleY = 1.1f
        holder.itemView.translationZ = 8f
        holder.logo.tag = "https://s/rancio.png"
        adapter.onViewRecycled(holder)
        assertEquals(1.0f, holder.itemView.scaleX, 0.001f)
        assertEquals(1.0f, holder.itemView.scaleY, 0.001f)
        assertEquals(0f, holder.itemView.translationZ, 0.001f)
        assertNull(holder.logo.tag)
    }

    @Test
    @Config(qualifiers = "television")
    fun `B2 no-regresion contenedor protegido y raiz focable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = browserRoot(context)
        // El wrap sigue siendo frontera de clip (contención).
        ChannelBrowserFragment().bindClipBoundary(root, isTv = true)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        assertTrue(com.tvplayer.app.ui.common.FocusScaleHelper.isProtected(wrap))
        // La raíz del item porta foco/click; el inner solo pinta.
        val holder = tvHolder(context)
        assertTrue(holder.itemView.isFocusable)
        assertFalse(holder.inner?.isFocusable ?: true)
    }

    @Test
    fun `B2 movil reciclado seguro sin inner`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = ChannelGridAdapter(onClick = {})
        val parent = androidx.recyclerview.widget.RecyclerView(context)
        parent.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        assertNull(holder.inner)
        holder.itemView.scaleX = 1.1f
        holder.logo.tag = "https://s/rancio.png"
        adapter.onViewRecycled(holder)
        assertEquals(1.0f, holder.itemView.scaleX, 0.001f)
        assertNull(holder.logo.tag)
    }
}
