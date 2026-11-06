package com.tvplayer.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.data.AppContainer
import com.tvplayer.app.ui.common.FocusScaleHelper
import kotlinx.coroutines.launch

/**
 * Ajustes: buffer del reproductor + proxy AceStream + limpieza por áreas
 * (favoritos / EPG / logos / todo, siempre con confirmación) y acerca-de con
 * el disclaimer exacto del spec §6.8.
 *
 * Nota: el gate de cleartext sigue activo (default OFF, sin interruptor).
 */
class SettingsFragment : Fragment() {

    companion object {
        const val TAG = "settings"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val container = (requireActivity().application as TpApp).container
        val wipeFavs = view.findViewById<Button>(R.id.settings_wipe_favs)
        val wipeEpg = view.findViewById<Button>(R.id.settings_wipe_epg)
        val wipeLogos = view.findViewById<Button>(R.id.settings_wipe_logos)
        val wipeAll = view.findViewById<Button>(R.id.settings_wipe_all)
        val bufMin = view.findViewById<android.widget.EditText>(R.id.settings_buffer_min)
        val bufMax = view.findViewById<android.widget.EditText>(R.id.settings_buffer_max)
        val bufBack = view.findViewById<android.widget.EditText>(R.id.settings_buffer_back)
        val bufSave = view.findViewById<Button>(R.id.settings_buffer_save)
        view.findViewById<TextView>(R.id.settings_about).text =
            getString(R.string.about_disclaimer_full) + "\n\n" + getString(
                R.string.settings_build,
                java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(
                    java.util.Date(
                        requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).lastUpdateTime,
                    ),
                ),
            )
        listOf(wipeFavs, wipeEpg, wipeLogos, wipeAll, bufSave).forEach { FocusScaleHelper.attach(it) }

        wipeFavs.setOnClickListener {
            confirm(getString(R.string.settings_wipe_favs)) {
                wipe { container().db.favouriteDao().clearAll() }
            }
        }
        wipeEpg.setOnClickListener {
            confirm(getString(R.string.settings_wipe_epg)) {
                wipe { container().db.epgDao().clearAll() }
            }
        }
        wipeLogos.setOnClickListener {
            confirm(getString(R.string.settings_wipe_logos)) {
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        container().imageLoader.diskCache?.directory?.let { dir ->
                            java.io.File(dir.toString()).deleteRecursively()
                        }
                    }
                    toast(R.string.settings_wiped)
                }
            }
        }
        wipeAll.setOnClickListener {
            confirm(getString(R.string.settings_wipe_all)) {
                wipe {
                    val db = container().db
                    db.channelDao().clearAll()
                    db.favouriteDao().clearAll()
                    db.epgDao().clearAll()
                    db.listDao().clearAll()
                }
            }
        }

        // Buffer del reproductor (spec §6): segundos en UI, ms en DAO; se aplica
        // al próximo play (el engine se crea por reproducción).
        lifecycleScope.launch {
            val current = com.tvplayer.app.data.BufferConfig.load(container.db)
            bufMin.setText((current.minMs / 1000).toString())
            bufMax.setText((current.maxMs / 1000).toString())
            bufBack.setText((current.backMs / 1000).toString())
        }
        bufSave.setOnClickListener {
            lifecycleScope.launch {
                val config = com.tvplayer.app.data.BufferConfig(
                    minMs = (bufMin.text.toString().toIntOrNull() ?: 15) * 1000,
                    maxMs = (bufMax.text.toString().toIntOrNull() ?: 30) * 1000,
                    backMs = (bufBack.text.toString().toIntOrNull() ?: 8) * 1000,
                )
                com.tvplayer.app.data.BufferConfig.save(container.db, config)
                val applied = com.tvplayer.app.data.BufferConfig.load(container.db)
                bufMin.setText((applied.minMs / 1000).toString())
                bufMax.setText((applied.maxMs / 1000).toString())
                bufBack.setText((applied.backMs / 1000).toString())
                Toast.makeText(requireContext(), R.string.settings_buffer_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun container() = (requireActivity().application as TpApp).container

    /** Confirmación antes de cualquier borrado. */
    private fun confirm(itemName: String, onYes: () -> Unit) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_confirm_title)
            .setMessage(getString(R.string.settings_confirm_wipe, itemName))
            .setPositiveButton(R.string.lists_delete) { _, _ -> onYes() }
            .setNegativeButton(R.string.lists_cancel, null)
            .show()
    }

    private fun wipe(block: suspend () -> Unit) {
        lifecycleScope.launch {
            runCatching { block() }
            toast(R.string.settings_wiped)
        }
    }

    private suspend fun toast(resId: Int) {
        val context = context ?: return
        Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
    }
}
