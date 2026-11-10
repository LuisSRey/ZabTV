package com.tvplayer.app.ui.channels

import android.content.Context
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fix Canales-móvil D1 (S4): fila móvil con altura fija y celdas fijas —
 * sin match_parent en cadena wrap_content. TV prohibido (sin cambios).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserLayoutTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    @Test
    fun `fila movil raiz 72dp fija`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val root = LayoutInflater.from(context).inflate(R.layout.item_channel, parent, false)
        assertEquals(dp(context, 72), root.layoutParams.height)
    }

    @Test
    fun `celda timeline movil 110x56dp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val cell = LayoutInflater.from(context).inflate(R.layout.item_channel_timeline, parent, false) as TextView
        assertEquals(dp(context, 110), cell.layoutParams.width)
        assertEquals(dp(context, 56), cell.layoutParams.height)
    }

    @Test
    fun `fila movil lleva tira timeline`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val root = LayoutInflater.from(context).inflate(R.layout.item_channel, parent, false)
        val strip = root.findViewById<LinearLayout>(R.id.channel_timeline)
        // La tira existe en móvil (el adapter la protege en TV con null-safe).
        assertEquals(true, strip != null)
    }
}
