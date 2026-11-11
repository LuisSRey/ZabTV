package com.tvplayer.app.ui.channels

import android.app.AlertDialog
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import com.tvplayer.app.R
import com.tvplayer.app.data.db.ListEntity
import com.tvplayer.app.ui.common.FocusScaleHelper

/**
 * Selector de playlists reutilizable (S4): menú móvil (PopupMenu) y diálogo
 * TV (AlertDialog con filas propias) sobre las MISMAS etiquetas en el MISMO
 * orden. Superficies con tokens propios vía overlays (fondo tp_surface,
 * texto tp_text, radio 10dp, elev 8dp); foco D-pad intacto (filas focables
 * + escala TV).
 */
object ListPicker {

    /** Overlay del menú: fondo tp_surface + texto tp_text. */
    val menuThemeRes: Int = R.style.ThemeOverlay_TVPlayer_ListPopup

    /** Overlay del diálogo: ventana dialog_bg + texto tp_text. */
    val dialogThemeRes: Int = R.style.ThemeOverlay_TVPlayer_ListDialog

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
