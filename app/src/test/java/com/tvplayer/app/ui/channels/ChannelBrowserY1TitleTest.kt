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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Y1 S1 (TV+escudo): el título inline baja del toprow a la columna media
 * como primer hijo (encima del selector; 30sp, no focable, 1L end; el
 * sidebar sigue a peso 1) y el toprow queda con el hero como hijo único.
 * Solo layout-television (+ réplica land-television); móvil intacto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserY1TitleTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun tvRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
    }

    @Test
    @Config(qualifiers = "television")
    fun `Y1 S1 tv titulo en media como primer hijo 30sp 1L end no focable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val title = root.findViewById<TextView>(R.id.browser_title_inline)
        assertTrue(title != null)
        val sidebar = root.findViewById<RecyclerView>(R.id.browser_sidebar)
        val selector = root.findViewById<TextView>(R.id.browser_list_selector)
        val column = sidebar.parent as ViewGroup
        // El título vive en la columna media (no en el toprow).
        assertEquals(column, title.parent as View)
        assertEquals(column, selector.parent as View)
        assertEquals(dp(context, 200), column.layoutParams.width)
        val order = (0 until column.childCount).map { column.getChildAt(it).id }
        assertEquals(
            listOf(R.id.browser_title_inline, R.id.browser_list_selector, R.id.browser_sidebar),
            order,
        )
        // 30sp acento bold 1 línea end centro-vertical, sin foco.
        assertEquals(context.getString(R.string.browser_title), title.text.toString())
        val expected = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 30f, context.resources.displayMetrics,
        )
        assertEquals(expected, title.textSize, 1.0f)
        assertEquals(context.getColor(R.color.tp_accent), title.currentTextColor)
        assertEquals(1, title.maxLines)
        assertEquals(android.text.TextUtils.TruncateAt.END, title.ellipsize)
        assertTrue(title.gravity and android.view.Gravity.CENTER_VERTICAL != 0)
        assertFalse(title.isFocusable)
        // En la columna: ancho total sin peso (el peso 1 sigue en el sidebar).
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, title.layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, title.layoutParams.height)
    }

    @Test
    @Config(qualifiers = "television")
    fun `Y1 S1 tv toprow sin titulo solo hero`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val toprow = root.findViewById<LinearLayout>(R.id.browser_toprow)
        assertTrue(toprow != null)
        val topIds = (0 until toprow.childCount).map { toprow.getChildAt(it).id }
        assertEquals(listOf(R.id.browser_hero), topIds)
        // El título ya no vive en el toprow.
        assertTrue(toprow.findViewById<View>(R.id.browser_title_inline) == null)
        assertTrue(root.findViewById<View>(R.id.browser_hero) != null)
    }

    @Test
    @Config(qualifiers = "television")
    fun `Y1 S1 tv sidebar sigue a peso1`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val sidebar = root.findViewById<RecyclerView>(R.id.browser_sidebar)
        val slp = sidebar.layoutParams as LinearLayout.LayoutParams
        assertEquals(0, slp.height)
        assertEquals(1f, slp.weight, 0.001f)
    }

    @Test
    @Config(qualifiers = "land-television")
    fun `Y1 S1 escudo replica titulo en media y toprow sin titulo`() {
        // Escudo land-TV: réplica exacta (diff solo comentario).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvRoot(context)
        val title = root.findViewById<TextView>(R.id.browser_title_inline)
        val sidebar = root.findViewById<RecyclerView>(R.id.browser_sidebar)
        val column = sidebar.parent as ViewGroup
        assertEquals(column, title.parent as View)
        val order = (0 until column.childCount).map { column.getChildAt(it).id }
        assertEquals(
            listOf(R.id.browser_title_inline, R.id.browser_list_selector, R.id.browser_sidebar),
            order,
        )
        assertFalse(title.isFocusable)
        val toprow = root.findViewById<LinearLayout>(R.id.browser_toprow)
        val topIds = (0 until toprow.childCount).map { toprow.getChildAt(it).id }
        assertEquals(listOf(R.id.browser_hero), topIds)
    }
}
