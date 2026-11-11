package com.tvplayer.app.ui.common

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S4 MarqueeOnFocus: reposo END + selected=false ↔ foco MARQUEE +
 * selected=true (repeatLimit 1, maxLines 1) en selector + group_label;
 * group_count nunca (sin marquee ni selected).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarqueeOnFocusTest {

    private fun label(): TextView =
        TextView(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `reposo end sin seleccionar repeat1 maxLines1`() {
        val label = label()
        MarqueeOnFocus.attach(label)
        assertEquals(TextUtils.TruncateAt.END, label.ellipsize)
        assertFalse(label.isSelected)
        assertEquals(1, label.marqueeRepeatLimit)
        assertEquals(1, label.maxLines)
    }

    @Test
    fun `foco marquee seleccionado y al salir end`() {
        val label = label()
        MarqueeOnFocus.attach(label)
        label.onFocusChangeListener.onFocusChange(label, true)
        assertEquals(TextUtils.TruncateAt.MARQUEE, label.ellipsize)
        assertTrue(label.isSelected)
        label.onFocusChangeListener.onFocusChange(label, false)
        assertEquals(TextUtils.TruncateAt.END, label.ellipsize)
        assertFalse(label.isSelected)
    }

    @Test
    fun `bind usa el foco del anchor y encadena el previo`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val label = TextView(context)
        val anchor = LinearLayout(context).apply { isFocusable = true }
        var prevFired = false
        anchor.onFocusChangeListener = View.OnFocusChangeListener { _, _ -> prevFired = true }
        MarqueeOnFocus.bind(label, anchor)
        anchor.onFocusChangeListener.onFocusChange(anchor, true)
        assertTrue(prevFired)
        assertEquals(TextUtils.TruncateAt.MARQUEE, label.ellipsize)
        assertTrue(label.isSelected)
        anchor.onFocusChangeListener.onFocusChange(anchor, false)
        assertEquals(TextUtils.TruncateAt.END, label.ellipsize)
        assertFalse(label.isSelected)
    }

    @Test
    @Config(qualifiers = "television")
    fun `tv group_count nunca marquee`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val row = LayoutInflater.from(context).inflate(R.layout.item_group, parent, false)
        val label = row.findViewById<TextView>(R.id.group_label)
        val count = row.findViewById<TextView>(R.id.group_count)
        // Cableado del adapter: solo el label va al marquee.
        MarqueeOnFocus.bind(label, row)
        row.onFocusChangeListener.onFocusChange(row, true)
        assertEquals(TextUtils.TruncateAt.MARQUEE, label.ellipsize)
        assertNotEquals(TextUtils.TruncateAt.MARQUEE, count.ellipsize)
        assertFalse(count.isSelected)
    }
}
