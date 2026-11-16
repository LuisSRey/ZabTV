package com.zabtv.app.ui.channels

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.PopupMenu
import com.zabtv.app.R
import com.zabtv.app.data.db.ListEntity
import com.zabtv.app.ui.common.FocusScaleHelper

/**
 * Selector de playlists reutilizable: burbuja móvil ([ListPopupWindow] vía
 * buildPopup/showPopup), menú legacy ([PopupMenu]) y diálogo TV
 * (AlertDialog con filas propias) sobre las MISMAS etiquetas en el MISMO
 * orden. Superficies con tokens propios (fondo dialog_bg: radio 10dp sobre
 * tp_surface, elev 8dp); foco D-pad intacto (filas focables + escala TV).
 */
object ListPicker {

    /** Overlay del menú: fondo tp_surface + texto tp_text. */
    val menuThemeRes: Int = R.style.ThemeOverlay_ZabTV_ListPopup

    /** Overlay del diálogo: ventana dialog_bg + texto tp_text. */
    val dialogThemeRes: Int = R.style.ThemeOverlay_ZabTV_ListDialog

    /** Elevación de la superficie del diálogo (8dp). */
    const val ELEVATION_DP = 8

    /**
     * Etiquetas en orden de lista, con fallback si el nombre viene en
     * blanco. Puro para test directo.
     */
    fun labels(lists: List<ListEntity>, fallback: String): List<String> =
        lists.map { it.name.ifBlank { fallback } }

    /**
     * Menú anclado (móvil): mismo PopupMenu de siempre, construido sobre el
     * overlay. [onPick] recibe el ÍNDICE en [labels].
     */
    fun buildMenu(anchor: View, labels: List<String>, onPick: (Int) -> Unit): PopupMenu {
        val popup = PopupMenu(ContextThemeWrapper(anchor.context, menuThemeRes), anchor)
        labels.forEachIndexed { index, label ->
            popup.menu.add(0, index, index, label)
        }
        popup.setOnMenuItemClickListener { item ->
            onPick(item.itemId)
            true
        }
        return popup
    }

    /** Menú anclado y mostrado. */
    fun showMenu(anchor: View, labels: List<String>, onPick: (Int) -> Unit) {
        buildMenu(anchor, labels, onPick).show()
    }

    /**
     * Ancho de la burbuja: el del anchor. Puro para test directo.
     */
    fun popupWidth(anchorWidth: Int): Int = anchorWidth

    /**
     * Fondo de la burbuja: dialog_bg (radio 10dp sobre tp_surface).
     */
    fun popupBackground(context: Context): Drawable =
        context.getDrawable(R.drawable.dialog_bg)!!

    /**
     * Elevación de la burbuja en px (8dp, la misma del diálogo).
     */
    fun popupElevationPx(context: Context): Float =
        ELEVATION_DP * context.resources.displayMetrics.density

    /**
     * Elevación de la ventana interna (O3): ListPopupWindow no expone API
     * pública de elevación en ningún nivel (verificado hasta API 35), así
     * que se aplica al PopupWindow interno vía reflexión con runCatching:
     * si un OEM renombra el campo, la burbuja abre igual sin elevación.
     */
    private fun applyPopupElevation(popup: ListPopupWindow, px: Float) {
        runCatching {
            val field = ListPopupWindow::class.java.getDeclaredField("mPopup")
            field.isAccessible = true
            (field.get(popup) as? android.widget.PopupWindow)?.elevation = px
        }
    }

    /**
     * Burbuja anclada (móvil, O3): [ListPopupWindow] modal con el MISMO
     * adapter de filas propias que el diálogo (item_list_picker 48dp
     * focable), fondo dialog_bg, elev 8dp y ancho = anchor.width.
     * [onPick] recibe el ÍNDICE en [labels]; el dismiss precede al pick
     * (sin reentradas ni leak: el caller cierra con dismiss()).
     */
    fun buildPopup(anchor: View, labels: List<String>, onPick: (Int) -> Unit): ListPopupWindow {
        val popup = ListPopupWindow(anchor.context)
        val adapter = object : ArrayAdapter<String>(anchor.context, R.layout.item_list_picker, R.id.list_picker_label, labels) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                super.getView(position, convertView, parent).also { FocusScaleHelper.attach(it) }
        }
        popup.setAdapter(adapter)
        popup.anchorView = anchor
        popup.isModal = true
        popup.width = popupWidth(anchor.width)
        popup.setBackgroundDrawable(popupBackground(anchor.context))
        applyPopupElevation(popup, popupElevationPx(anchor.context))
        popup.setOnItemClickListener { _, _, position, _ ->
            popup.dismiss()
            onPick(position)
        }
        return popup
    }

    /**
     * Burbuja anclada y mostrada. Con lista vacía NO abre (null, sin
     * excepción ni ventana: el caller ya filtra, esto es el cerrojo).
     */
    fun showPopup(anchor: View, labels: List<String>, onPick: (Int) -> Unit): ListPopupWindow? {
        if (labels.isEmpty()) return null
        return buildPopup(anchor, labels, onPick).also { it.show() }
    }

    /**
     * Diálogo D-pad (TV): mismas etiquetas y orden que el menú, con filas
     * propias focables. [onPick] recibe el ÍNDICE en [labels].
     */
    fun buildDialog(context: Context, labels: List<String>, onPick: (Int) -> Unit): AlertDialog {
        val themed = ContextThemeWrapper(context, dialogThemeRes)
        val adapter = object : ArrayAdapter<String>(themed, R.layout.item_list_picker, R.id.list_picker_label, labels) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                super.getView(position, convertView, parent).also { FocusScaleHelper.attach(it) }
        }
        return AlertDialog.Builder(themed)
            .setAdapter(adapter) { _, which -> onPick(which) }
            .create()
    }

    /** Diálogo mostrado, con la superficie elevada 8dp. */
    fun showDialog(context: Context, labels: List<String>, onPick: (Int) -> Unit): AlertDialog {
        val dialog = buildDialog(context, labels, onPick)
        dialog.show()
        val density = context.resources.displayMetrics.density
        // Fondo explícito (AppCompat ignora el windowBackground del overlay
        // y pondría su abc de 2dp): radio 10dp sobre tp_surface garantizado.
        dialog.window?.setBackgroundDrawable(context.getDrawable(R.drawable.dialog_bg))
        dialog.window?.decorView?.elevation = ELEVATION_DP * density
        return dialog
    }
}
