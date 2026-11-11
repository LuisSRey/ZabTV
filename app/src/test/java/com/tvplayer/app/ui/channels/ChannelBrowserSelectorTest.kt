package com.tvplayer.app.ui.channels

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
import com.tvplayer.app.data.db.ListEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * O2 selector (S3): placeholder lists_name en XML + activeLabel/selectorText
 * puros (siempre sufijo ▾) + snapshot/restore del texto en rotación sin
 * re-query + fallback sin listas. TDD RED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserSelectorTest {

    private fun lists() = listOf(
        ListEntity(id = 1, name = "Casa", sourceUrl = "https://x/a.m3u"),
        ListEntity(id = 2, name = "", sourceUrl = ""),
        ListEntity(id = 3, name = "Fuera", sourceUrl = "https://x/c.m3u"),
    )

    @Test
    fun `portrait selector placeholder lists_name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, FrameLayout(context), false)
        val selector = root.findViewById<TextView>(R.id.browser_list_selector)
        assertEquals(context.getString(R.string.lists_name), selector.text.toString())
    }

    @Test
    @Config(qualifiers = "land")
    fun `land selector placeholder lists_name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, FrameLayout(context), false)
        val selector = root.findViewById<TextView>(R.id.browser_list_selector)
        assertEquals(context.getString(R.string.lists_name), selector.text.toString())
    }

    @Test
    fun `activeLabel resuelve nombre con fallback`() {
        assertEquals("Casa", ChannelBrowserFragment.activeLabel(lists(), 1, "Nombre"))
        assertEquals("Nombre", ChannelBrowserFragment.activeLabel(lists(), 2, "Nombre"))
        assertEquals("Fuera", ChannelBrowserFragment.activeLabel(lists(), 3, "Nombre"))
        assertEquals("Nombre", ChannelBrowserFragment.activeLabel(lists(), 99, "Nombre"))
        assertEquals("Nombre", ChannelBrowserFragment.activeLabel(emptyList(), 1, "Nombre"))
    }

    @Test
    fun `selectorText siempre con sufijo`() {
        assertEquals("Casa ▾", ChannelBrowserFragment.selectorText("Casa"))
        assertEquals("Nombre ▾", ChannelBrowserFragment.selectorText("Nombre"))
    }

    @Test
    fun `snapshot guarda y sanea texto del selector`() {
        assertEquals(
            BrowserRotateHelper.Snapshot(true, 4, 2, "Casa ▾"),
            BrowserRotateHelper.snapshot(true, 4, 2, "Casa ▾"),
        )
        assertEquals(
            BrowserRotateHelper.Snapshot(false, 0, 0, ""),
            BrowserRotateHelper.snapshot(false, 0, 0),
        )
    }
}
