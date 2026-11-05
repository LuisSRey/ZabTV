package com.tvplayer.app.ui.lists

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import com.tvplayer.app.R

/**
 * Alta de lista por URL (Task 11): nombre + URL + User-Agent opcional
 * (algunos proveedores lo exigen). La validación de esquema la hace
 * ImportService/UrlPolicer al importar.
 */
class AddListDialog(
    private val onSave: (name: String, url: String, userAgent: String?) -> Unit,
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val name = EditText(context).apply { hint = getString(R.string.lists_name) }
        val url = EditText(context).apply {
            hint = getString(R.string.lists_url)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val userAgent = EditText(context).apply { hint = getString(R.string.lists_ua) }
        layout.addView(name)
        layout.addView(url)
        layout.addView(userAgent)
        return AlertDialog.Builder(context)
            .setTitle(R.string.lists_add)
            .setView(layout)
            .setPositiveButton(R.string.lists_save) { _, _ ->
                onSave(
                    name.text.toString().trim(),
                    url.text.toString().trim(),
                    userAgent.text.toString().trim().ifBlank { null },
                )
            }
            .setNegativeButton(R.string.lists_cancel, null)
            .create()
    }
}
