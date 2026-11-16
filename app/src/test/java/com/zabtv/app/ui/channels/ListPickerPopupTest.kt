package com.zabtv.app.ui.channels

import android.app.Activity
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * O3 burbuja (S4): ListPicker.buildPopup/showPopup/popupWidth —
 * ListPopupWindow modal con adapter item_list_picker (48dp focable), fondo
 * dialog_bg (radio 10dp, #1C1C1E), elev 8dp, ancho = anchor.width; vacía no
 * abre; dismiss sin leak; mismos datos/orden que el diálogo TV. TDD RED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListPickerPopupTest {

    private fun labels() = listOf("Casa", "Nombre", "Fuera")

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /** Anchor con ancho real (medido y posicionado como tras layout) y con
     * ventana (contenido de la actividad: el show necesita token). */
    private fun anchor(activity: Activity, width: Int = 500): TextView =
        TextView(activity).apply {
            measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(context, 48), View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, measuredWidth, measuredHeight)
            activity.setContentView(this)
        }

    private fun activity(): Activity =
        Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun `popupWidth es el ancho del anchor`() {
        assertEquals(500, ListPicker.popupWidth(500))
        assertEquals(0, ListPicker.popupWidth(0))
    }

    @Test
    fun `buildPopup modal y ancho igual al anchor`() {
        val activity = activity()
        val anchor = anchor(activity)
        val popup = ListPicker.buildPopup(anchor, labels()) {}
        assertTrue(popup.isModal)
        assertEquals(anchor.width, popup.width)
        // Fondo aplicado de verdad (getter público desde API 11).
        val bg = popup.background as GradientDrawable
        val expectedRadius = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, 10f, activity.resources.displayMetrics,
        )
        assertEquals(expectedRadius, bg.cornerRadius, 0.01f)
        assertEquals(activity.getColor(R.color.tp_surface), bg.color?.defaultColor)
        // Elevación aplicada a la ventana interna (read-back por reflexión).
        val expectedElev = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, 8f, activity.resources.displayMetrics,
        )
        assertEquals(expectedElev, popupWindowElevation(popup) ?: -1f, 0.01f)
    }

    private fun popupWindowElevation(popup: android.widget.ListPopupWindow): Float? =
        runCatching {
            val field = android.widget.ListPopupWindow::class.java.getDeclaredField("mPopup")
            field.isAccessible = true
            (field.get(popup) as? android.widget.PopupWindow)?.elevation
        }.getOrNull()

    @Test
    fun `popup fondo dialog_bg radio 10dp sobre surface`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bg = ListPicker.popupBackground(context) as GradientDrawable
        val expected = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, 10f, context.resources.displayMetrics,
        )
        assertEquals(expected, bg.cornerRadius, 0.01f)
        assertEquals(context.getColor(R.color.tp_surface), bg.color?.defaultColor)
    }

    @Test
    fun `popup usa elevacion 8dp`() {
        assertEquals(8, ListPicker.ELEVATION_DP)
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 8f, context.resources.displayMetrics,
            ),
            ListPicker.popupElevationPx(context),
            0.01f,
        )
    }

    @Test
    fun `showPopup mismos datos y orden con fila 48dp focable`() {
        val activity = activity()
        val anchor = anchor(activity)
        val popup = ListPicker.showPopup(anchor, labels()) {}
        assertNotNull(popup)
        try {
            assertTrue(popup!!.isShowing)
            val list = popup.listView
            assertNotNull(list)
            assertEquals(3, list!!.adapter.count)
            assertEquals(labels(), (0 until list.adapter.count).map { list.adapter.getItem(it) })
            val row = list.adapter.getView(0, null, list)
            assertEquals(dp(activity, 48), row.layoutParams.height)
            assertTrue(row.isFocusable)
        } finally {
            popup?.dismiss()
        }
        assertFalse(popup!!.isShowing)
    }

    @Test
    fun `showPopup vacia no abre`() {
        val activity = activity()
        assertNull(ListPicker.showPopup(anchor(activity), emptyList()) {})
    }

    @Test
    fun `showPopup pick con indice y dismiss sin leak`() {
        val activity = activity()
        val picked = mutableListOf<Int>()
        val popup = ListPicker.showPopup(anchor(activity), labels(), picked::add)
        assertNotNull(popup)
        assertTrue(popup!!.isShowing)
        try {
            val list = popup.listView!!
            val row = list.adapter.getView(1, null, list)
            list.performItemClick(row, 1, list.adapter.getItemId(1))
            assertEquals(listOf(1), picked)
        } finally {
            if (popup.isShowing) popup.dismiss()
        }
        assertFalse(popup.isShowing)
    }
}
