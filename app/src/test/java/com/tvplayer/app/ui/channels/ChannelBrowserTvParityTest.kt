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
    fun `S1 tv gap selector-sidebar 20dp mas selector elevation 16dp`() {
        // U1 S1: gap 12→20dp + selector con elevation 16dp (escudo: el
        // selector focado con escala 1.1 dibuja por encima del sidebar).
        // Resto intacto (safe 56dp, gap 32dp, nextFocus §F2).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvBrowserRoot(context)
        val selector = root.findViewById<TextView>(R.id.browser_list_selector)
        val sidebar = root.findViewById<RecyclerView>(R.id.browser_sidebar)
        val lp = sidebar.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals(dp(context, 20), lp.topMargin)
        assertEquals(dp(context, 16).toFloat(), selector.elevation, 1f)
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
        assertEquals(dp(context, 32), (root.findViewById<View>(R.id.browser_grid_wrap).parent as View).layoutParams.let {
            (it as ViewGroup.MarginLayoutParams).marginStart
        })
        assertTrue(root.findViewById<View>(R.id.browser_hero) != null)
        assertTrue(root.findViewById<View>(R.id.browser_hero_play) != null)
        assertTrue(root.findViewById<View>(R.id.browser_slots) != null)
        assertTrue(root.findViewById<RecyclerView>(R.id.browser_grid) != null)
        assertTrue(root.findViewById<TextView>(R.id.browser_empty) != null)
    }

    @Test
    fun `X1 tv columnas sin titulo raiz con toprow titulo-inline y hero lateral`() {
        // X1 S1 (motivo: el título raíz robaba 1 fila en 720p; el título
        // baja a la fila del hero y el hero pasa a lateral compacto):
        // raíz → solo browser_columns (marginTop 0); derecha →
        // [toprow, slots, wrap, empty]; toprow horizontal →
        // [title_inline, hero]; hero mismo id en wrap_content + minWidth 320.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvBrowserRoot(context) as ViewGroup
        assertEquals(1, root.childCount)
        val columns = root.getChildAt(0)
        assertEquals(R.id.browser_columns, columns.id)
        assertEquals(0, (columns.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        assertEquals(wrap, grid.parent as View)
        val rightPanel = wrap.parent as ViewGroup
        val ids = (0 until rightPanel.childCount).map { rightPanel.getChildAt(it).id }
        assertEquals(
            listOf(
                R.id.browser_toprow, R.id.browser_slots,
                R.id.browser_grid_wrap, R.id.browser_empty,
            ),
            ids,
        )
        val toprow = root.findViewById<LinearLayout>(R.id.browser_toprow)
        assertEquals(LinearLayout.HORIZONTAL, toprow.orientation)
        val topIds = (0 until toprow.childCount).map { toprow.getChildAt(it).id }
        assertEquals(listOf(R.id.browser_title_inline, R.id.browser_hero), topIds)
        val title = root.findViewById<TextView>(R.id.browser_title_inline)
        assertEquals(context.getString(R.string.browser_title), title.text.toString())
        val tlp = title.layoutParams as LinearLayout.LayoutParams
        assertEquals(0, tlp.width)
        assertEquals(1f, tlp.weight, 0.001f)
        assertTrue(title.gravity and android.view.Gravity.CENTER_VERTICAL != 0)
        val expectedTitle = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 30f, context.resources.displayMetrics,
        )
        assertEquals(expectedTitle, title.textSize, 1.0f)
        assertEquals(context.getColor(R.color.tp_accent), title.currentTextColor)
        assertEquals(1, title.maxLines)
        assertEquals(android.text.TextUtils.TruncateAt.END, title.ellipsize)
        val hero = root.findViewById<View>(R.id.browser_hero)
        assertEquals(toprow, hero.parent as View)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, hero.layoutParams.width)
        assertEquals(dp(context, 320), hero.minimumWidth)
    }

    @Test
    fun `X1 S2 hero lateral compacto interior intacto VER y cap160`() {
        // X1 S2 (motivo: el hero lateral conserva el interior mini W3 S3):
        // padding 4, título 16sp 1L, now 12sp 1L mt2, progreso 4dp mt4,
        // VER wrap/minW 96x48 mt4 con nextFocus intactos + cap TV 160.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvBrowserRoot(context)
        val hero = root.findViewById<View>(R.id.browser_hero)
        assertEquals(root.findViewById<View>(R.id.browser_toprow), hero.parent as View)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, hero.layoutParams.width)
        assertEquals(dp(context, 320), hero.minimumWidth)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, hero.layoutParams.height)
        assertEquals(dp(context, 4), hero.paddingLeft)
        assertEquals(dp(context, 4), hero.paddingTop)
        assertEquals(dp(context, 4), hero.paddingRight)
        assertEquals(dp(context, 4), hero.paddingBottom)
        val play = root.findViewById<android.widget.Button>(R.id.browser_hero_play)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, play.layoutParams.width)
        assertEquals(dp(context, 96), play.minWidth)
        assertEquals(dp(context, 48), play.layoutParams.height)
        assertEquals(dp(context, 48), play.minimumHeight)
        assertEquals(dp(context, 4), (play.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        assertEquals(R.id.browser_grid, play.nextFocusDownId)
        assertEquals(R.id.browser_sidebar, play.nextFocusLeftId)
        assertEquals(R.id.browser_list_selector, play.nextFocusUpId)
        assertEquals(160, ChannelBrowserFragment.HERO_MAX_DP_TV)
    }

    @Test
    fun `S2 tv hero mini padding4 titulo16sp now12sp VER96x48`() {
        // W3 S3 (reemplaza U1 S2): hero mini apilado — padding 4 +
        // título 16sp 1 línea + now 12sp 1 línea marginTop 2 + progreso
        // 4dp marginTop 4 + VER wrap/minWidth 96 x 48 marginTop 4.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvBrowserRoot(context)
        val hero = root.findViewById<View>(R.id.browser_hero)
        assertEquals(dp(context, 4), hero.paddingLeft)
        assertEquals(dp(context, 4), hero.paddingTop)
        val title = root.findViewById<TextView>(R.id.browser_hero_title)
        val expectedTitle = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 16f, context.resources.displayMetrics,
        )
        assertEquals(expectedTitle, title.textSize, 1.0f)
        assertEquals(1, title.maxLines)
        val now = root.findViewById<TextView>(R.id.browser_hero_now)
        val expectedNow = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 12f, context.resources.displayMetrics,
        )
        assertEquals(expectedNow, now.textSize, 1.0f)
        assertEquals(1, now.maxLines)
        assertEquals(
            dp(context, 2),
            (now.layoutParams as ViewGroup.MarginLayoutParams).topMargin,
        )
        val progress = root.findViewById<android.widget.ProgressBar>(R.id.browser_hero_progress)
        assertTrue(progress != null)
        assertEquals(dp(context, 4), progress.minimumHeight)
        assertEquals(
            dp(context, 4),
            (progress.layoutParams as ViewGroup.MarginLayoutParams).topMargin,
        )
        val play = root.findViewById<android.widget.Button>(R.id.browser_hero_play)
        assertEquals(dp(context, 48), play.layoutParams.height)
        assertEquals(dp(context, 48), play.minimumHeight)
        assertEquals(dp(context, 96), play.minWidth)
        assertEquals(
            dp(context, 4),
            (play.layoutParams as ViewGroup.MarginLayoutParams).topMargin,
        )
    }

    @Test
    fun `S2 tv slots 6 celdas 0dp peso 12sp sin HSV ni spacer`() {
        // S2: browser_slots pasa a LinearLayout horizontal con 6 celdas
        // (0dp/peso 1, 12sp secondary, 1 línea), sin HSV ni spacer, entre
        // el hero y la grid; la grid conserva peso 1 + marginTop 12dp.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvBrowserRoot(context)
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        assertTrue(slots != null)
        assertEquals(LinearLayout.HORIZONTAL, slots.orientation)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, slots.layoutParams.width)
        assertEquals(
            dp(context, 12),
            (slots.layoutParams as ViewGroup.MarginLayoutParams).topMargin,
        )
        // W2 S2: tira sin fondo ni elevación, solo aire vertical 2dp.
        assertEquals(0f, slots.elevation, 0.1f)
        assertTrue(slots.background == null)
        assertEquals(dp(context, 2), slots.paddingTop)
        assertEquals(dp(context, 2), slots.paddingBottom)
        assertTrue(slots.parent !is android.widget.HorizontalScrollView)
        assertEquals(6, slots.childCount)
        val ids = listOf(
            R.id.browser_slot_0, R.id.browser_slot_1, R.id.browser_slot_2,
            R.id.browser_slot_3, R.id.browser_slot_4, R.id.browser_slot_5,
        )
        val expectedSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 12f, context.resources.displayMetrics,
        )
        for (i in 0..5) {
            val cell = slots.getChildAt(i) as TextView
            assertEquals(ids[i], cell.id)
            val lp = cell.layoutParams as LinearLayout.LayoutParams
            assertEquals(0, lp.width)
            assertEquals(1f, lp.weight, 0.001f)
            assertEquals(expectedSize, cell.textSize, 1.0f)
            assertEquals(context.getColor(R.color.tp_text_secondary), cell.currentTextColor)
            assertEquals(1, cell.maxLines)
            assertEquals(android.text.TextUtils.TruncateAt.END, cell.ellipsize)
        }
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        val content = grid.parent.parent as ViewGroup
        // X1 S1 (motivo: el hero baja al toprow lateral, ya no es hijo
        // directo): el toprow precede a los slots y el hero vive dentro.
        val toprow = root.findViewById<View>(R.id.browser_toprow)
        assertEquals(content, toprow.parent as View)
        assertTrue(content.indexOfChild(toprow) < content.indexOfChild(slots))
        assertEquals(toprow, root.findViewById<View>(R.id.browser_hero).parent as View)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        assertTrue(content.indexOfChild(slots) < content.indexOfChild(wrap))
        val wlp = wrap.layoutParams as LinearLayout.LayoutParams
        assertEquals(1f, wlp.weight, 0.001f)
        // X1 S3 (motivo: el aire vertical vive en el paddingTop 12 de la
        // grid, sin margen; pega slots.bottom == wrap.top).
        assertEquals(0, (grid.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
    }

    @Test
    fun `S2 tile_logo_placeholder layer-list surface2 mas logo`() {
        // U1 S2: placeholder de logo = layer-list (fondo surface2 + marca).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val d = context.getDrawable(R.drawable.tile_logo_placeholder)
        assertTrue(d is android.graphics.drawable.LayerDrawable)
        assertEquals(2, (d as android.graphics.drawable.LayerDrawable).numberOfLayers)
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
    fun `S4 tv grupo reposo end maxLines1 repeat1 sin seleccionar`() {
        // S4 (motivo S6): en reposo sigue END + maxLines 1 (el MARQUEE solo
        // entra con el foco vía MarqueeOnFocus, que el adapter cablea por
        // holder); group_count intacto y sin marquee.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val root = LayoutInflater.from(context).inflate(R.layout.item_group, parent, false)
        val label = root.findViewById<TextView>(R.id.group_label)
        assertEquals(android.text.TextUtils.TruncateAt.END, label.ellipsize)
        assertEquals(1, label.maxLines)
        assertEquals(1, label.marqueeRepeatLimit)
        assertEquals(false, label.isSelected)
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
    fun `S1 tv grid minHeight 192dp peso intacto`() {
        // S1 contención: el wrap porta peso 1 + minHeight 192dp; la grid
        // vive dentro en match_parent sin peso ni minHeight.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvBrowserRoot(context)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        assertEquals(dp(context, 192), wrap.minimumHeight)
        val wlp = wrap.layoutParams as LinearLayout.LayoutParams
        assertEquals(1f, wlp.weight, 0.001f)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertEquals(wrap, grid.parent as View)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, grid.layoutParams.height)
        assertEquals(0, grid.minimumHeight)
    }

    @Test
    fun `S1 tv hero progress 4dp min-max`() {
        // S1: barra del hero fina (4dp) vía min+max, resto intacto.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvBrowserRoot(context)
        val progress = root.findViewById<android.widget.ProgressBar>(R.id.browser_hero_progress)
        assertEquals(dp(context, 4), progress.minimumHeight)
        val f = android.widget.ProgressBar::class.java
            .getDeclaredField("mMaxHeight").apply { isAccessible = true }
        assertEquals(dp(context, 4), f.getInt(progress))
    }

    @Test
    fun `S1 hero cap TV 160dp y movil 140dp`() {
        // S1: el cap aplica también en TV (160dp); móvil sigue en 140dp.
        assertEquals(140, ChannelBrowserFragment.HERO_MAX_DP)
        assertEquals(160, ChannelBrowserFragment.HERO_MAX_DP_TV)
        assertEquals((2f * 160).toInt(), ChannelBrowserFragment.heroMaxPxFor(2f, true))
        assertEquals((2f * 140).toInt(), ChannelBrowserFragment.heroMaxPxFor(2f, false))
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
