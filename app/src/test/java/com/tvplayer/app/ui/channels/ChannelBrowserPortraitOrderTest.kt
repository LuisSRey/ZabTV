package com.tvplayer.app.ui.channels

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
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
 * O1 portrait (S2): el bloque HSV+slots baja bajo el sidebar, encima del
 * grid — orden título→hero→selector→sidebar→slots→grid→vacío, con mismos
 * ids/attrs. Land/TV intactos. TDD RED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserPortraitOrderTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /** Id efectivo de cada hijo directo: el HSV sin id cuenta como slots. */
    private fun childOrder(root: View): List<Int> {
        val group = root as ViewGroup
        return (0 until group.childCount).map { i ->
            val child = group.getChildAt(i)
            if (child.id != View.NO_ID) {
                child.id
            } else if (child.findViewById<View>(R.id.browser_slots) != null) {
                R.id.browser_slots
            } else {
                child.id
            }
        }
    }

    @Test
    fun `portrait orden titulo hero selector sidebar slots grid vacio`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, FrameLayout(context), false)
        val order = childOrder(root).filter {
            it == R.id.browser_hero || it == R.id.browser_list_selector ||
                it == R.id.browser_sidebar || it == R.id.browser_slots ||
                it == R.id.browser_grid || it == R.id.browser_empty
        }
        assertEquals(
            listOf(
                R.id.browser_hero, R.id.browser_list_selector, R.id.browser_sidebar,
                R.id.browser_slots, R.id.browser_grid, R.id.browser_empty,
            ),
            order,
        )
    }

    @Test
    fun `portrait slots sigue en HSV hermano del hero con margin 12dp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, FrameLayout(context), false)
        val slots = root.findViewById<LinearLayout>(R.id.browser_slots)
        assertTrue(slots.parent is HorizontalScrollView)
        assertEquals(
            dp(context, 12),
            ((slots.parent as View).layoutParams as ViewGroup.MarginLayoutParams).topMargin,
        )
        assertEquals(7, slots.childCount)
        // El sidebar queda encima de los slots y la grid debajo.
        val sidebar = root.findViewById<RecyclerView>(R.id.browser_sidebar)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        val parent = root as ViewGroup
        fun indexOf(v: View): Int {
            var p = v
            while (p.parent !== parent) p = p.parent as View
            return (0 until parent.childCount).first { parent.getChildAt(it) === p }
        }
        assertTrue(indexOf(sidebar) < indexOf(slots.parent as View))
        assertTrue(indexOf(slots.parent as View) < indexOf(grid))
    }

    @Test
    @Config(qualifiers = "land")
    fun `land orden intacto hero slots grid en panel dcha`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, FrameLayout(context), false)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        val rightPanel = grid.parent as ViewGroup
        val order = (0 until rightPanel.childCount).map { i ->
            val child = rightPanel.getChildAt(i)
            if (child.id != View.NO_ID) {
                child.id
            } else if (child.findViewById<View>(R.id.browser_slots) != null) {
                R.id.browser_slots
            } else {
                child.id
            }
        }.filter {
            it == R.id.browser_hero || it == R.id.browser_slots ||
                it == R.id.browser_grid || it == R.id.browser_empty
        }
        assertEquals(
            listOf(R.id.browser_hero, R.id.browser_slots, R.id.browser_grid, R.id.browser_empty),
            order,
        )
    }

    @Test
    @Config(qualifiers = "television")
    fun `tv orden intacto S3`() {
        // S3 (motivo S6): el selector sube a la columna media encima de los
        // filtros; la columna derecha queda hero→slots→grid→vacío.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, FrameLayout(context), false)
        val grid = root.findViewById<RecyclerView>(R.id.browser_grid)
        val wrap = root.findViewById<View>(R.id.browser_grid_wrap)
        assertEquals(wrap, grid.parent as View)
        val rightPanel = wrap.parent as ViewGroup
        val ids = (0 until rightPanel.childCount).map { rightPanel.getChildAt(it).id }
        assertEquals(
            listOf(
                R.id.browser_hero, R.id.browser_slots,
                R.id.browser_grid_wrap, R.id.browser_empty,
            ),
            ids,
        )
        val sidebar = root.findViewById<RecyclerView>(R.id.browser_sidebar)
        val column = sidebar.parent as ViewGroup
        val columnIds = (0 until column.childCount).map { column.getChildAt(it).id }
        assertEquals(listOf(R.id.browser_list_selector, R.id.browser_sidebar), columnIds)
    }
}
