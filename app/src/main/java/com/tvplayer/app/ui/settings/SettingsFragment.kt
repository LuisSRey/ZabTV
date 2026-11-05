package com.tvplayer.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
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
 * Ajustes (Task 11): opt-in de HTTP sin cifrar (con advertencia, default OFF),
 * limpieza por áreas (favoritos / EPG / logos / todo) y acerca-de con el
 * disclaimer exacto del spec §6.8.
 *
 * Nota: tamaños de buffer de Media3 y UA por lista viven en el alta de lista;
 * el ajuste fino de buffer queda diferido (TODO en Media3Engine, Task 8).
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
        val httpSwitch = view.findViewById<Switch>(R.id.settings_http)
        val wipeFavs = view.findViewById<Button>(R.id.settings_wipe_favs)
        val wipeEpg = view.findViewById<Button>(R.id.settings_wipe_epg)
        val wipeLogos = view.findViewById<Button>(R.id.settings_wipe_logos)
        val wipeAll = view.findViewById<Button>(R.id.settings_wipe_all)
        view.findViewById<TextView>(R.id.settings_about).text = getString(R.string.about_disclaimer_full)
        listOf(httpSwitch, wipeFavs, wipeEpg, wipeLogos, wipeAll).forEach { FocusScaleHelper.attach(it) }

        lifecycleScope.launch {
            httpSwitch.isChecked = container.db.settingsDao()
                .get(AppContainer.KEY_ALLOW_HTTP, "false") == "true"
        }
        httpSwitch.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch {
                container.db.settingsDao().put(AppContainer.KEY_ALLOW_HTTP, checked.toString())
                container.setAllowHttp(checked)
                Toast.makeText(
                    requireContext(),
                    if (checked) R.string.settings_http_on else R.string.settings_http_off,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        wipeFavs.setOnClickListener { wipe { container().db.favouriteDao().clearAll() } }
        wipeEpg.setOnClickListener { wipe { container().db.epgDao().clearAll() } }
        wipeLogos.setOnClickListener {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    container().imageLoader.diskCache?.directory?.let { dir ->
                        java.io.File(dir.toString()).deleteRecursively()
                    }
                }
                toast(R.string.settings_wiped)
            }
        }
        wipeAll.setOnClickListener {
            wipe {
                val db = container().db
                db.channelDao().clearAll()
                db.favouriteDao().clearAll()
                db.epgDao().clearAll()
                db.listDao().clearAll()
            }
        }
    }

    private fun container() = (requireActivity().application as TpApp).container

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
