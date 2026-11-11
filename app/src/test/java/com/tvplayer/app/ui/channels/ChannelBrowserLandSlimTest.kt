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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * O4 land slim (S1): columna izq 320dp→248dp + item_group land propio
 * (match_parent×48dp, marginBottom 8dp, mismos ids, tile_focus_bg,
 * focable). Portrait/TV intactos. TDD RED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserLandSlimTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    @Test
    @Config(qualifiers = "land")
    fun `land columna izq 248dp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, FrameLayout(context), false)
        val sidebar = root.findViewById<RecyclerView>(R.id.browser_sidebar)
        val leftPanel = sidebar.parent as View
        assertEquals(dp(context, 248), leftPanel.layoutParams.width)
    }

    @Test
    @Config(qualifiers = "land")
    fun `land item_group match_parent x 48dp con marginBottom 8dp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val row = LayoutInflater.from(context).inflate(R.layout.item_group, parent, false)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, row.layoutParams.width)
        assertEquals(dp(context, 48), row.layoutParams.height)
        assertEquals(
            dp(context, 8),
            (row.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin,
        )
        assertTrue(row.findViewById<TextView>(R.id.group_label) != null)
        assertTrue(row.findViewById<TextView>(R.id.group_count) != null)
        assertTrue(row.background != null)
        assertTrue(row.isFocusable)
    }

    @Test
    fun `portrait item_group chip intacto`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val row = LayoutInflater.from(context).inflate(R.layout.item_group, parent, false)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, row.layoutParams.width)
        assertEquals(dp(context, 40), row.layoutParams.height)
        assertTrue(row.findViewById<TextView>(R.id.group_label) != null)
        assertTrue(row.findViewById<TextView>(R.id.group_count) != null)
    }

    @Test
    @Config(qualifiers = "television")
    fun `tv item_group intacto`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val row = LayoutInflater.from(context).inflate(R.layout.item_group, parent, false)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, row.layoutParams.width)
        assertEquals(dp(context, 52), row.minimumHeight)
        assertTrue(row.findViewById<TextView>(R.id.group_label) != null)
        assertTrue(row.findViewById<TextView>(R.id.group_count) != null)
    }
}
