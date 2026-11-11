package com.tvplayer.app.ui.channels

import android.content.Context
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S4: browser apaisado móvil (layout-land: panel izq 248dp + contenido dcha
 * peso 1, mismos ids, hero compacto) + escudo TV-apaisado (sigue mandando
 * layout-television) + refreshOnRotate (helpers puros) + vacío solo con
 * 0 canales confirmados.
 */
@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class ChannelBrowserLandTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun landRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
    }

    @Test
    @Config(qualifiers = "land")
    fun `land panel izq 248dp y contenido dcha peso1`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = landRoot(context) as LinearLayout
        assertEquals(LinearLayout.HORIZONTAL, root.orientation)
        val sidebar = root.findViewById<RecyclerView>(R.id.browser_sidebar)
        val leftPanel = sidebar.parent as View
        assertEquals(dp(context, 248), leftPanel.layoutParams.width)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        val rightPanel = grid.parent as View
        val lp = rightPanel.layoutParams as LinearLayout.LayoutParams
        assertEquals(0, lp.width)
        assertEquals(1f, lp.weight, 0.001f)
    }

    @Test
    @Config(qualifiers = "land")
    fun `land mismos ids y hero compacto`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = landRoot(context)
        assertTrue(root.findViewById<TextView>(R.id.browser_list_selector) != null)
        assertTrue(root.findViewById<View>(R.id.browser_hero) != null)
        assertTrue(root.findViewById<TextView>(R.id.browser_hero_title) != null)
        assertTrue(root.findViewById<TextView>(R.id.browser_hero_now) != null)
        assertTrue(root.findViewById<View>(R.id.browser_hero_progress) != null)
        assertTrue(root.findViewById<View>(R.id.browser_hero_play) != null)
        assertTrue(root.findViewById<View>(R.id.browser_slots) != null)
        assertTrue(root.findViewById<RecyclerView>(R.id.browser_grid) != null)
        assertTrue(root.findViewById<TextView>(R.id.browser_empty) != null)
        // Hero compacto: título 16sp (el vertical usa 20sp).
        val title = root.findViewById<TextView>(R.id.browser_hero_title)
        val expected = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 16f, context.resources.displayMetrics,
        )
        assertEquals(expected, title.textSize, 1.0f)
    }

    @Test
    @Config(qualifiers = "land")
    fun `land cabecera 6 cortes de 110dp con spacer offset en HSV`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = landRoot(context)
        // MISMO id, ahora LinearLayout horizontal (el TextView vertical sigue intacto).
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        assertTrue(slots != null)
        assertEquals(LinearLayout.HORIZONTAL, slots.orientation)
        // Envuelto en HSV para alinear con las celdas de la fila.
        assertTrue(slots.parent is HorizontalScrollView)
        // Spacer (= offset del timeline en item_channel) + 6 cortes.
        assertEquals(7, slots.childCount)
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.browser_timeline_offset),
            (slots.getChildAt(0).layoutParams as LinearLayout.LayoutParams).width,
        )
        assertEquals(
            dp(context, 229),
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
        }
    }

    @Test
    @Config(qualifiers = "land")
    fun `land grid intacta`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = landRoot(context)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        assertTrue(grid != null)
        val lp = grid.layoutParams as LinearLayout.LayoutParams
        assertEquals(1f, lp.weight, 0.001f)
        assertEquals(dp(context, 96), grid.minimumHeight)
    }

    @Test
    @Config(qualifiers = "land-television")
    fun `tv apaisado conserva layout television`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = landRoot(context)
        // Escudo: sin layout-land-television, el land móvil secuestraría la TV
        // (orientación precede a ui-mode). Debe seguir la TV: safe 56dp.
        assertEquals(dp(context, 56), root.paddingLeft)
        assertEquals(dp(context, 56), root.paddingTop)
        val sidebar = root.findViewById<RecyclerView>(R.id.browser_sidebar)
        assertEquals(dp(context, 260), sidebar.layoutParams.width)
    }

    @Test
    @Config(qualifiers = "land")
    fun `land hero no contiene ni desplaza la cabecera`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = landRoot(context)
        val slots = root.findViewById<View>(R.id.browser_slots)
        assertTrue(slots != null)
        // La cabecera vive en un HSV hermano del hero (nunca dentro): el
        // colapso del hero no la mueve ni la oculta.
        assertTrue(slots.parent is HorizontalScrollView)
        var p: ViewParent? = slots.parent
        while (p != null) {
            assertTrue((p as? View)?.id != R.id.browser_hero)
            p = p.parent
        }
    }

    @Test
    fun `emptyVisible solo con 0 canales y refresh resuelto`() {
        assertTrue(ChannelBrowserFragment.emptyVisible(0, true))
        assertFalse(ChannelBrowserFragment.emptyVisible(0, false))
        assertFalse(ChannelBrowserFragment.emptyVisible(3, true))
        assertFalse(ChannelBrowserFragment.emptyVisible(3, false))
    }

    @Test
    fun `sidebarOrientation vertical en tv y en apaisado movil`() {
        assertEquals(
            LinearLayoutManager.VERTICAL,
            ChannelBrowserFragment.sidebarOrientation(true, Configuration.ORIENTATION_LANDSCAPE),
        )
        assertEquals(
            LinearLayoutManager.VERTICAL,
            ChannelBrowserFragment.sidebarOrientation(true, Configuration.ORIENTATION_PORTRAIT),
        )
        assertEquals(
            LinearLayoutManager.VERTICAL,
            ChannelBrowserFragment.sidebarOrientation(false, Configuration.ORIENTATION_LANDSCAPE),
        )
        assertEquals(
            LinearLayoutManager.HORIZONTAL,
            ChannelBrowserFragment.sidebarOrientation(false, Configuration.ORIENTATION_PORTRAIT),
        )
        assertEquals(
            LinearLayoutManager.HORIZONTAL,
            ChannelBrowserFragment.sidebarOrientation(false, Configuration.ORIENTATION_UNDEFINED),
        )
    }
}
