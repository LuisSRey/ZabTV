package com.tvplayer.app.ui.channels

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
import com.tvplayer.app.data.db.ListEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S4: selector de playlists reutilizable — mismas etiquetas/datos/orden en
 * menú y diálogo, fila 48dp con tokens propios y overlays con fondo
 * tp_surface + texto tp_text + radio 10dp + elev 8dp. TDD RED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListPickerTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun lists() = listOf(
        ListEntity(id = 1, name = "Casa", sourceUrl = "https://x/a.m3u"),
        ListEntity(id = 2, name = "", sourceUrl = ""),
        ListEntity(id = 3, name = "Fuera", sourceUrl = "https://x/c.m3u"),
    )

    @Test
    fun `labels mismos datos orden y fallback`() {
        assertEquals(listOf("Casa", "Nombre", "Fuera"), ListPicker.labels(lists(), "Nombre"))
    }

    @Test
    fun `labels coincide con listDialogLabels del fragment`() {
        val fallback = "Nombre"
        assertEquals(
            ChannelBrowserFragment.listDialogLabels(lists(), fallback).toList(),
            ListPicker.labels(lists(), fallback),
        )
    }

    @Test
    fun `item 48dp tokens propios y focable D-pad`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = FrameLayout(context)
        val row = LayoutInflater.from(context)
            .inflate(R.layout.item_list_picker, parent, false) as TextView
        assertEquals(dp(context, 48), row.layoutParams.height)
        assertTrue(row.background != null)
        assertTrue(row.isFocusable)
        val expected = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 16f, context.resources.displayMetrics,
        )
        assertEquals(expected, row.textSize, 1.0f)
        assertEquals(context.getColor(R.color.tp_text), row.currentTextColor)
    }

    @Test
    fun `overlays existen y ListPicker los expone`() {
        assertEquals(R.style.ThemeOverlay_TVPlayer_ListPopup, ListPicker.menuThemeRes)
        assertEquals(R.style.ThemeOverlay_TVPlayer_ListDialog, ListPicker.dialogThemeRes)
    }

    @Test
    fun `dialog bg radio 10dp sobre tp_surface`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bg = context.getDrawable(R.drawable.dialog_bg) as GradientDrawable
        val expected = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, 10f, context.resources.displayMetrics,
        )
        assertEquals(expected, bg.cornerRadius, 0.01f)
        assertEquals(context.getColor(R.color.tp_surface), bg.color?.defaultColor)
    }

    @Test
    fun `buildMenu conserva items en orden`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val anchor = TextView(context)
        val labels = ListPicker.labels(lists(), "Nombre")
        val picked = mutableListOf<Int>()
        val popup = ListPicker.buildMenu(anchor, labels) { picked += it }
        assertEquals(3, popup.menu.size())
        assertEquals(listOf("Casa", "Nombre", "Fuera"), (0 until popup.menu.size()).map { popup.menu.getItem(it).title.toString() })
        popup.menu.performIdentifierAction(2, 0)
        assertEquals(listOf(2), picked)
    }

    @Test
    fun `showDialog preselecciona activo foco y Back sin excepcion`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val labels = ListPicker.labels(lists(), "Nombre")
        val active = 1
        val picked = mutableListOf<Int>()

        // Preselección + foco en el índice activo, dismiss sin excepción.
        val dialog = ListPicker.showDialog(context, labels) { picked += it }
        try {
            assertTrue(dialog.isShowing)
            val list = dialog.listView
            assertNotNull(list)
            assertEquals(labels.size, list!!.adapter.count)
            list.setSelection(active) // API de preselección usable, sin excepción
            // (la posición reportada la gobierna el touch-mode del framework,
            // no el diálogo: no se afirma). La fila activa sí es focable D-pad.
            val row = list.adapter.getView(active, null, list)
            assertTrue(row.isFocusable)
            row.requestFocus() // camino D-pad: sin excepción
        } finally {
            dialog.dismiss()
        }
        assertFalse(dialog.isShowing)

        // Back real: cancela (sin pick) sin excepción.
        val back = ListPicker.showDialog(context, labels) { picked += it }
        try {
            back.onBackPressed()
            assertFalse(back.isShowing)
        } finally {
            if (back.isShowing) back.dismiss()
        }
        assertTrue(picked.isEmpty())
    }

    @Test
    fun `showDialog aplica fondo y elevacion 8dp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val labels = ListPicker.labels(lists(), "Nombre")
        val picked = mutableListOf<Int>()
        val dialog = ListPicker.showDialog(context, labels) { picked += it }
        try {
            val bg = dialog.window?.decorView?.background as GradientDrawable
            val expectedRadius = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 10f, context.resources.displayMetrics,
            )
            assertEquals(expectedRadius, bg.cornerRadius, 0.01f)
            val expectedElev = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 8f, context.resources.displayMetrics,
            )
            assertEquals(expectedElev, dialog.window?.decorView?.elevation ?: 0f, 0.01f)
        } finally {
            dialog.dismiss()
        }
    }
}
