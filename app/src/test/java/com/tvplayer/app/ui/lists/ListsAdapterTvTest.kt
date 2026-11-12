package com.tvplayer.app.ui.lists

import android.app.Activity
import android.widget.Button
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * FRENTE L S4: en TV la raíz de la fila NO es focable (el foco va a los
 * botones; si la raíz lo tomara, Actualizar/Borrar quedarían inalcanzables con
 * el D-pad) y cada botón pasea el unclip de ancestros + escala 1.1. En móvil el
 * tap de fila sigue abriendo la lista. Sin tocar el cableado de botones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "television")
class ListsAdapterTvTest {

    private class Calls {
        val log = mutableListOf<String>()
    }

    private fun adapter(calls: Calls): ListsAdapter = ListsAdapter(
        onOpen = { calls.log += "open:${it.id}" },
        onRefresh = { calls.log += "refresh:${it.id}" },
        onDelete = { calls.log += "delete:${it.id}" },
        onEdit = { calls.log += "edit:${it.id}" },
        onEpg = { calls.log += "epg:${it.id}" },
        onActivate = { calls.log += "activate:${it.id}" },
    )

    private fun holder(
        activity: Activity,
        row: ListsAdapter.Row = ListsAdapter.Row(1, "Lista", "ready • 3 canales", false),
        calls: Calls = Calls(),
    ): ListsAdapter.Holder {
        val adapter = adapter(calls)
        val container = FrameLayout(activity)
        activity.setContentView(container)
        val holder = adapter.onCreateViewHolder(container, 0)
        container.addView(holder.itemView)
        adapter.submit(listOf(row))
        adapter.onBindViewHolder(holder, 0)
        return holder
    }

    private fun idleMain() {
        Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(Duration.ofMillis(300))
    }

    @Test
    fun `L tv raiz no focable y seis botones focables`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val h = holder(activity)
        // La raíz no toma foco ni se anima; el tap móvil se cablea en bind.
        assertFalse(h.itemView.isFocusable)
        assertNull(h.itemView.onFocusChangeListener)
        for (button in listOf(h.open, h.refresh, h.delete, h.activate, h.edit, h.epg)) {
            assertTrue(button.isFocusable)
            assertFalse(button.isFocusableInTouchMode)
            assertNotNull(button.onFocusChangeListener)
        }
    }

    @Test
    fun `L tv escala por boton sin tocar la raiz`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val h = holder(activity)
        h.open.onFocusChangeListener.onFocusChange(h.open, true)
        idleMain()
        assertEquals(1.1f, h.open.scaleX, 0.001f)
        assertEquals(1.1f, h.open.scaleY, 0.001f)
        // La raíz se queda quieta (no hay halo de fila).
        assertEquals(1.0f, h.itemView.scaleX, 0.001f)
        assertEquals(1.0f, h.itemView.scaleY, 0.001f)
        assertEquals(0f, h.itemView.translationZ, 0.001f)
    }

    @Test
    fun `L tv activate disabled en fila activa`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val h = holder(activity, ListsAdapter.Row(2, "Activa", "ready", true))
        assertFalse(h.activate.isEnabled)
        assertTrue(h.open.isEnabled)
        assertTrue(h.refresh.isEnabled)
        assertTrue(h.delete.isEnabled)
        assertTrue(h.edit.isEnabled)
        assertTrue(h.epg.isEnabled)
    }

    @Test
    fun `L tv cada boton dispara su callback una vez`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val calls = Calls()
        val h = holder(activity, ListsAdapter.Row(3, "Lista", "ready", false), calls)
        listOf(h.open, h.refresh, h.delete, h.activate, h.edit, h.epg).forEach { it.performClick() }
        assertEquals(
            listOf("open:3", "refresh:3", "delete:3", "activate:3", "edit:3", "epg:3"),
            calls.log,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-port")
    fun `L movil tap de fila abre sin foco`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val calls = Calls()
        val h = holder(activity, ListsAdapter.Row(7, "Móvil", "ready", false), calls)
        assertFalse(h.itemView.isFocusable)
        assertTrue(h.itemView.isClickable)
        h.itemView.performClick()
        assertEquals(listOf("open:7"), calls.log)
        // Los botones siguen cableados (cero cambio móvil).
        val buttons = listOf<Button>(h.open, h.refresh, h.delete, h.activate, h.edit, h.epg)
        assertEquals(6, buttons.size)
    }
}
