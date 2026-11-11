package com.tvplayer.app.ui.channels

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
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
 * Paridad TV D1+D2 (S1) + columna media S3: el browser TV lleva el selector
 * de playlist (dropdown 48dp) ENCIMA de los filtros en la columna media de
 * 200dp (antes 260dp con el selector en la columna derecha: ~60dp más de
 * grid en 720p), con nextFocus explícitos §F2. Resto intacto (safe 56dp,
 * gap 32dp).
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
    fun `S3 tv columna media 200dp selector encima de filtros resto intacto`() {
        // S3 (motivo S6): media 260→200dp + selector a la columna media
        // encima de los filtros; el ancho vive en la columna y el sidebar
        // rellena (match_parent).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvBrowserRoot(context)
        assertEquals(dp(context, 56), root.paddingLeft)
        assertEquals(dp(context, 56), root.paddingTop)
        assertEquals(dp(context, 56), root.paddingRight)
        assertEquals(dp(context, 56), root.paddingBottom)
        val sidebar = root.findViewById<RecyclerView>(R.id.browser_sidebar)
        val selector = root.findViewById<TextView>(R.id.browser_list_selector)
        val column = sidebar.parent as View
        assertEquals(selector.parent, column)
        assertEquals(dp(context, 200), column.layoutParams.width)
        val columnGroup = column as ViewGroup
        assertTrue(columnGroup.indexOfChild(selector) < columnGroup.indexOfChild(sidebar))
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertEquals(dp(context, 32), (grid.parent as View).layoutParams.let {
            (it as ViewGroup.MarginLayoutParams).marginStart
        })
        assertTrue(root.findViewById<View>(R.id.browser_hero) != null)
        assertTrue(root.findViewById<View>(R.id.browser_hero_play) != null)
        assertTrue(root.findViewById<View>(R.id.browser_slots) != null)
        assertTrue(root.findViewById<RecyclerView>(R.id.browser_grid) != null)
        assertTrue(root.findViewById<TextView>(R.id.browser_empty) != null)
    }

    @Test
    fun `S3 tv nextFocus mapa F2 sin trampas`() {
        // §F2: nav_browse<->selector, selector v sidebar, sidebar ^selector
        // >grid <nav, grid ^selector <sidebar, hero_play ^selector v grid
        // <sidebar. Nada apunta al hero en exclusiva (puede estar GONE).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvBrowserRoot(context)
        val selector = root.findViewById<TextView>(R.id.browser_list_selector)
        val sidebar = root.findViewById<RecyclerView>(R.id.browser_sidebar)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        val play = root.findViewById<View>(R.id.browser_hero_play)
        assertEquals(R.id.nav_browse, selector.nextFocusLeftId)
        assertEquals(R.id.browser_sidebar, selector.nextFocusDownId)
        assertEquals(R.id.browser_list_selector, sidebar.nextFocusUpId)
        assertEquals(R.id.browser_grid, sidebar.nextFocusRightId)
        assertEquals(R.id.nav_browse, sidebar.nextFocusLeftId)
        assertEquals(R.id.browser_list_selector, grid.nextFocusUpId)
        assertEquals(R.id.browser_sidebar, grid.nextFocusLeftId)
        assertEquals(R.id.browser_list_selector, play.nextFocusUpId)
        assertEquals(R.id.browser_grid, play.nextFocusDownId)
        assertEquals(R.id.browser_sidebar, play.nextFocusLeftId)
        // Sin trampas: el selector no se apunta a sí mismo y su salida
        // principal (abajo) es el sidebar, siempre visible (el hero puede
        // estar GONE, así que nada lo apunta como único camino).
        assertNotEquals(R.id.browser_list_selector, selector.nextFocusDownId)
    }

    @Test
    fun `S2 tv fila vertical focable con cabecera y timeline`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val root = LayoutInflater.from(context).inflate(R.layout.item_channel, parent, false) as LinearLayout
        assertEquals(LinearLayout.VERTICAL, root.orientation)
        assertTrue(root.isFocusable)
        assertTrue(root.background != null)
        assertTrue(root.findViewById<View>(R.id.channel_number) != null)
        val logo = root.findViewById<android.widget.ImageView>(R.id.channel_logo)
        assertEquals(dp(context, 96), logo.layoutParams.width)
        assertEquals(dp(context, 56), logo.layoutParams.height)
        assertTrue(root.findViewById<TextView>(R.id.channel_name) != null)
        assertTrue(root.findViewById<TextView>(R.id.channel_epg) != null)
        val strip = root.findViewById<LinearLayout>(R.id.channel_timeline)
        assertTrue(strip != null)
        assertEquals(LinearLayout.HORIZONTAL, strip.orientation)
    }

    @Test
    fun `S2 tv celda timeline peso igual 60dp end 2 lineas no focable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val cell = LayoutInflater.from(context)
            .inflate(R.layout.item_channel_timeline, parent, false) as TextView
        val lp = cell.layoutParams as LinearLayout.LayoutParams
        assertEquals(0, lp.width)
        assertEquals(1f, lp.weight, 0.001f)
        assertEquals(dp(context, 60), lp.height)
        assertEquals(android.text.TextUtils.TruncateAt.END, cell.ellipsize)
        assertEquals(2, cell.maxLines)
        assertEquals(false, cell.isFocusable)
        val expected = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 11f, context.resources.displayMetrics,
        )
        assertEquals(expected, cell.textSize, 1.0f)
    }

    @Test
    fun `S2 tv adapter infla 6 celdas sin tocar codigo`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = ChannelGridAdapter(onClick = {})
        val parent = RecyclerView(context)
        parent.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        // El adapter intacto (6 vía DEFAULT_SLOT_COUNT) ahora pinta tira en TV.
        assertEquals(6, holder.timeline?.childCount)
    }

    @Test
    fun `S3 tv grupo end maxLines1 sin marquee`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val root = LayoutInflater.from(context).inflate(R.layout.item_group, parent, false)
        val label = root.findViewById<TextView>(R.id.group_label)
        assertEquals(android.text.TextUtils.TruncateAt.END, label.ellipsize)
        assertEquals(1, label.maxLines)
        assertTrue(root.findViewById<TextView>(R.id.group_count) != null)
    }

    @Test
    fun `S4 dialogo TV mapea nombres con fallback lists_name`() {
        val lists = listOf(
            com.tvplayer.app.data.db.ListEntity(id = 1, name = "Casa", sourceUrl = "http://x/a.m3u"),
            com.tvplayer.app.data.db.ListEntity(id = 2, name = "", sourceUrl = ""),
        )
        assertEquals(
            listOf("Casa", "Nombre"),
            ChannelBrowserFragment.listDialogLabels(lists, "Nombre").toList(),
        )
    }

    @Test
    fun `S4 movil conserva PopupMenu y TV usa dialogo`() {
        // El wiring existe en ambos caminos: showListMenu (móvil) y
        // showListDialog (TV, AlertDialog con el mismo onListPicked).
        val names = ChannelBrowserFragment::class.java.declaredMethods.map { it.name }
        assertTrue(names.contains("showListMenu"))
        assertTrue(names.contains("showListDialog"))
    }
}
