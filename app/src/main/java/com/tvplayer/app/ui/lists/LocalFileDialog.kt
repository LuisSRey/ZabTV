package com.tvplayer.app.ui.lists

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvplayer.app.ui.common.FocusScaleHelper
import java.io.File

/**
 * Navegador local de .m3u (ruling usuario): para Fire TV sin picker del
 * sistema. Solo API ≤29 (en 30+ el permiso no da acceso a .m3u ajenos).
 * Carpetas con "/", "../" para subir; al elegir se devuelve el File.
 */
class LocalFileDialog(
    private val onPick: (File) -> Unit,
) : DialogFragment() {

    @Suppress("DEPRECATION")
    private val root: File = Environment.getExternalStorageDirectory()

    @Suppress("DEPRECATION")
    private var dir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        .takeIf { it.isDirectory } ?: root

    private lateinit var adapter: Adapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val pad = (16 * resources.displayMetrics.density).toInt()
        adapter = Adapter()
        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@LocalFileDialog.adapter
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (420 * resources.displayMetrics.density).toInt(),
            )
        }
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(list)
        }
        refresh()
        return AlertDialog.Builder(context)
            .setTitle(dir.name.ifBlank { "/" })
            .setView(layout)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun refresh() {
        adapter.rows = LocalFiles.listEntries(dir, root)
        dialog?.setTitle(dir.name.ifBlank { "/" })
    }

    private fun onRow(entry: LocalFiles.Entry) {
        when {
            entry.isUp || entry.isDir -> {
                dir = entry.file
                refresh()
            }
            else -> {
                dismiss()
                onPick(entry.file)
            }
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.Holder>() {
        var rows: List<LocalFiles.Entry> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        inner class Holder(val text: TextView) : RecyclerView.ViewHolder(text)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val pad = (12 * parent.resources.displayMetrics.density).toInt()
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setPadding(pad, pad, pad, pad)
                textSize = 17f
            }
            FocusScaleHelper.attach(tv)
            return Holder(tv)
        }

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = rows[position]
            holder.text.text = when {
                entry.isUp -> "../"
                entry.isDir -> entry.file.name + "/"
                else -> entry.file.name
            }
            holder.text.setOnClickListener { onRow(entry) }
        }
    }
}
