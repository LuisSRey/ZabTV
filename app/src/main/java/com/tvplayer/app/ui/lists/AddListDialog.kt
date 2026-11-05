package com.tvplayer.app.ui.lists

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import com.tvplayer.app.R

/**
 * Alta de lista (Tasks 11/12): modo URL (nombre + URL + UA opcional) o modo
 * Portal Xtream (nombre + host + usuario + password). La validación de esquema
 * la hacen XtreamLogin/UrlPolicer al importar; las credenciales solo viajan
 * a SecureCredentials, nunca a logs ni Toasts.
 */
class AddListDialog(
    private val onSave: (AddListInput) -> Unit,
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val modeRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val urlMode = Button(context).apply { text = getString(R.string.lists_mode_url) }
        val portalMode = Button(context).apply { text = getString(R.string.lists_mode_portal) }
        modeRow.addView(urlMode, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        modeRow.addView(portalMode, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        layout.addView(modeRow)

        val name = EditText(context).apply { hint = getString(R.string.lists_name) }
        layout.addView(name)

        val urlGroup = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val url = EditText(context).apply {
            hint = getString(R.string.lists_url)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        val userAgent = EditText(context).apply { hint = getString(R.string.lists_ua) }
        urlGroup.addView(url)
        urlGroup.addView(userAgent)
        layout.addView(urlGroup)

        val portalGroup = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val host = EditText(context).apply {
            hint = getString(R.string.lists_host)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        val username = EditText(context).apply { hint = getString(R.string.lists_user) }
        val password = EditText(context).apply {
            hint = getString(R.string.lists_pass)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        portalGroup.addView(host)
        portalGroup.addView(username)
        portalGroup.addView(password)
        layout.addView(portalGroup)

        var mode: Mode = Mode.URL
        val sync = {
            urlGroup.visibility = if (mode == Mode.URL) View.VISIBLE else View.GONE
            portalGroup.visibility = if (mode == Mode.PORTAL) View.VISIBLE else View.GONE
        }
        urlMode.setOnClickListener { mode = Mode.URL; sync() }
        portalMode.setOnClickListener { mode = Mode.PORTAL; sync() }

        return AlertDialog.Builder(context)
            .setTitle(R.string.lists_add)
            .setView(layout)
            .setPositiveButton(R.string.lists_save) { _, _ ->
                val input = if (mode == Mode.URL) {
                    AddListInput.Url(
                        name = name.text.toString().trim(),
                        url = url.text.toString().trim(),
                        userAgent = userAgent.text.toString().trim().ifBlank { null },
                    )
                } else {
                    AddListInput.Portal(
                        name = name.text.toString().trim(),
                        host = host.text.toString().trim(),
                        username = username.text.toString(),
                        password = password.text.toString(),
                    )
                }
                onSave(input)
            }
            .setNegativeButton(R.string.lists_cancel, null)
            .create()
    }

    private enum class Mode { URL, PORTAL }
}

/** Resultado del diálogo: o URL directa o credenciales de portal. */
sealed interface AddListInput {
    data class Url(val name: String, val url: String, val userAgent: String?) : AddListInput
    data class Portal(val name: String, val host: String, val username: String, val password: String) : AddListInput
}
