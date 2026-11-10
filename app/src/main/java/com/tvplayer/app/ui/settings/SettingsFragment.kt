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
        wireProxySection(view, container)
        wireDohSection(view, container)
        wireExternalSection(view, container)
    }

    /**
     * S4: toggle global del reproductor externo (key external_global, default
     * OFF) + advertencia de que el externo no hereda proxy/DNS.
     */
    private fun wireExternalSection(view: View, container: AppContainer) {
        val global = view.findViewById<android.widget.Switch>(R.id.settings_external_global)
        lifecycleScope.launch {
            global.isChecked = container.currentPolicy.externalGlobal
        }
        global.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch {
                container.db.settingsDao().put(AppContainer.KEY_EXTERNAL_GLOBAL, checked.toString())
                container.refreshNetPolicy()
            }
        }
    }

    private fun container() = (requireActivity().application as TpApp).container

    /**
     * S1: sección proxy. Guarda host/puerto/tipo en SettingsDao y las
     * credenciales en ProxyCreds (cifrado); al guardar se reconstruye el
     * OkHttp único. Probar hace un GET con timeout de 8 s sobre la política
     * en vigor y muestra OK/código/error SIN loguear credenciales.
     */
    private fun wireProxySection(view: View, container: AppContainer) {
        val enable = view.findViewById<android.widget.Switch>(R.id.settings_proxy_enable)
        val host = view.findViewById<android.widget.EditText>(R.id.settings_proxy_host)
        val port = view.findViewById<android.widget.EditText>(R.id.settings_proxy_port)
        val type = view.findViewById<android.widget.Spinner>(R.id.settings_proxy_type)
        val user = view.findViewById<android.widget.EditText>(R.id.settings_proxy_user)
        val pass = view.findViewById<android.widget.EditText>(R.id.settings_proxy_pass)
        val testUrl = view.findViewById<android.widget.EditText>(R.id.settings_proxy_test_url)
        val testBtn = view.findViewById<Button>(R.id.settings_proxy_test)
        val saveBtn = view.findViewById<Button>(R.id.settings_proxy_save)
        val result = view.findViewById<TextView>(R.id.settings_proxy_result)
        listOf(testBtn, saveBtn).forEach { FocusScaleHelper.attach(it) }

        lifecycleScope.launch {
            val policy = container.currentPolicy.proxy
            enable.isChecked = policy.enabled
            host.setText(policy.host)
            port.setText(policy.port.toString())
            type.setSelection(if (policy.type == com.tvplayer.app.data.net.ProxyType.SOCKS) 1 else 0)
            val creds = runCatching { container.proxyCreds.load() }.getOrNull()
            user.setText(creds?.first.orEmpty())
            // La contraseña no se rellena: solo se sobrescribe si se escribe una nueva.
        }
        saveBtn.setOnClickListener {
            lifecycleScope.launch {
                persistProxy(container, enable.isChecked, host.text.toString(), port.text.toString(),
                    if (type.selectedItemPosition == 1) "SOCKS" else "HTTP",
                    user.text.toString(), pass.text.toString())
                pass.text.clear()
                Toast.makeText(requireContext(), R.string.settings_proxy_saved, Toast.LENGTH_SHORT).show()
            }
        }
        testBtn.setOnClickListener {
            result.visibility = View.VISIBLE
            result.text = getString(R.string.lists_refreshing)
            lifecycleScope.launch {
                persistProxy(container, enable.isChecked, host.text.toString(), port.text.toString(),
                    if (type.selectedItemPosition == 1) "SOCKS" else "HTTP",
                    user.text.toString(), pass.text.toString())
                val url = testUrl.text.toString().trim()
                if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                    result.text = getString(R.string.settings_proxy_error, "URL http(s) inválida")
                    return@launch
                }
                result.text = probeUrl(container, url)
            }
        }
    }

    private suspend fun persistProxy(
        container: AppContainer,
        enabled: Boolean,
        host: String,
        port: String,
        type: String,
        user: String,
        pass: String,
    ) {
        val dao = container.db.settingsDao()
        dao.put(com.tvplayer.app.data.net.ProxyConfig.KEY_ENABLED, enabled.toString())
        dao.put(com.tvplayer.app.data.net.ProxyConfig.KEY_HOST, host.trim())
        dao.put(com.tvplayer.app.data.net.ProxyConfig.KEY_PORT, (port.toIntOrNull() ?: 8080).toString())
        dao.put(com.tvplayer.app.data.net.ProxyConfig.KEY_TYPE, type)
        val u = user.trim()
        if (u.isNotEmpty() || pass.isNotEmpty()) {
            runCatching { container.proxyCreds.save(u, pass) }
        } else if (u.isEmpty()) {
            runCatching { container.proxyCreds.clear() }
        }
        container.refreshNetPolicy()
        runCatching { coil.Coil.setImageLoader(container.imageLoader) }
    }

    /**
     * S2: selector DoH (Sistema/Cloudflare/Google/Personalizado) + URL custom.
     * Custom inválida → fallback System + toast (DohConfig.from).
     */
    private fun wireDohSection(view: View, container: AppContainer) {
        val mode = view.findViewById<android.widget.Spinner>(R.id.settings_doh_mode)
        val custom = view.findViewById<android.widget.EditText>(R.id.settings_doh_custom)
        val save = view.findViewById<Button>(R.id.settings_doh_save)
        FocusScaleHelper.attach(save)
        lifecycleScope.launch {
            val dao = container.db.settingsDao()
            val current = com.tvplayer.app.data.net.DohConfig.from(
                dao.get(com.tvplayer.app.data.net.DohConfig.KEY_MODE, "system"),
                dao.get(com.tvplayer.app.data.net.DohConfig.KEY_CUSTOM_URL, ""),
            )
            mode.setSelection(
                when (current) {
                    is com.tvplayer.app.data.net.DohConfig.Cloudflare -> 1
                    is com.tvplayer.app.data.net.DohConfig.Google -> 2
                    is com.tvplayer.app.data.net.DohConfig.Custom -> 3
                    else -> 0
                },
            )
            custom.setText((current as? com.tvplayer.app.data.net.DohConfig.Custom)?.url.orEmpty())
        }
        save.setOnClickListener {
            lifecycleScope.launch {
                val modeStr = when (mode.selectedItemPosition) {
                    1 -> com.tvplayer.app.data.net.DohConfig.MODE_CLOUDFLARE
                    2 -> com.tvplayer.app.data.net.DohConfig.MODE_GOOGLE
                    3 -> com.tvplayer.app.data.net.DohConfig.MODE_CUSTOM
                    else -> com.tvplayer.app.data.net.DohConfig.MODE_SYSTEM
                }
                val customUrl = custom.text.toString().trim()
                val resolved = com.tvplayer.app.data.net.DohConfig.from(modeStr, customUrl)
                if (modeStr == com.tvplayer.app.data.net.DohConfig.MODE_CUSTOM &&
                    resolved is com.tvplayer.app.data.net.DohConfig.System
                ) {
                    Toast.makeText(requireContext(), R.string.settings_doh_invalid, Toast.LENGTH_LONG).show()
                }
                val dao = container.db.settingsDao()
                dao.put(
                    com.tvplayer.app.data.net.DohConfig.KEY_MODE,
                    com.tvplayer.app.data.net.DohConfig.modeOf(resolved),
                )
                dao.put(com.tvplayer.app.data.net.DohConfig.KEY_CUSTOM_URL, customUrl)
                container.refreshNetPolicy()
                runCatching { coil.Coil.setImageLoader(container.imageLoader) }
                Toast.makeText(requireContext(), R.string.settings_doh_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** GET con timeout de 8 s. Devuelve el mensaje a mostrar; nunca loguea credenciales. */
    private suspend fun probeUrl(container: AppContainer, url: String): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = container.okHttp.newBuilder()
                    .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                client.newCall(okhttp3.Request.Builder().url(url).head().build()).execute().use { resp ->
                    if (resp.isSuccessful) getString(R.string.settings_proxy_ok, resp.code)
                    else getString(R.string.settings_proxy_code, resp.code)
                }
            } catch (_: Exception) {
                // HEAD puede no estar permitido: reintentar con GET acotado.
                try {
                    val client = container.okHttp.newBuilder()
                        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                        .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { resp ->
                        resp.close()
                        if (resp.isSuccessful) getString(R.string.settings_proxy_ok, resp.code)
                        else getString(R.string.settings_proxy_code, resp.code)
                    }
                } catch (e: Exception) {
                    getString(R.string.settings_proxy_error, e.javaClass.simpleName)
                }
            }
        }

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
