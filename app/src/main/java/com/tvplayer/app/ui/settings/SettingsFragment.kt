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
 * Nota: el gate de cleartext se controla desde esta pantalla (interruptor
 * allow_http, default OFF; SECCION-02 · D1/A3-01). Un solo dato (KEY_ALLOW_HTTP)
 * alimenta a CleartextGate (transporte) y a ExternalGate (ruta externa).
 */
class SettingsFragment : Fragment() {

    companion object {
        const val TAG = "settings"
    }

    /** SECCION-06: refreshNetPolicy lanzado al entrar; las secciones lo esperan. */
    private var policyRefresh: kotlinx.coroutines.Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val container = (requireActivity().application as TpApp).container
        // SECCION-06 (D11/A3-06): la política del transporte se sincroniza con
        // el DAO ANTES de pintar cualquier sección; las corrutinas de wiring
        // la esperan (idempotente y barato: rebuild del OkHttp único).
        policyRefresh = lifecycleScope.launch { container.refreshNetPolicy() }
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
                    clearCoilDiskCache(container().imageLoader)
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
     *
     * SECCION-02 (D1/A3-01): encima del toggle va el interruptor
     * «Permitir HTTP sin cifrar» (key allow_http, default OFF). Es el ÚNICO
     * switch de http://: escribe KEY_ALLOW_HTTP y ese dato alimenta a
     * CleartextGate (vía refreshNetPolicy) y a ExternalGate. El init lee el
     * DAO FRESCO (no currentPolicy) para no arrastrar caché rancia.
     *
     * Debajo del toggle va el selector de reproductor preferido
     * (key external_player_package, "" = Preguntar siempre): lista con
     * icono+nombre de cada app detectada. Con el global en OFF se oculta;
     * al volver a ON reaparece con la selección guardada. Sin apps
     * detectadas se muestra un texto informativo en vez de lista vacía.
     */
    private fun wireExternalSection(view: View, container: AppContainer) {
        val allowHttp = view.findViewById<android.widget.Switch>(R.id.settings_allow_http)
        val httpState = view.findViewById<TextView>(R.id.settings_http_state)
        val global = view.findViewById<android.widget.Switch>(R.id.settings_external_global)
        val label = view.findViewById<TextView>(R.id.settings_external_player_label)
        val spinner = view.findViewById<android.widget.Spinner>(R.id.settings_external_player)
        val empty = view.findViewById<TextView>(R.id.settings_external_player_empty)
        FocusScaleHelper.attach(allowHttp)
        FocusScaleHelper.attach(spinner)

        fun paintSelector(enabled: Boolean, players: List<com.tvplayer.app.engine.ExternalPlayerInfo>) {
            val visible = if (enabled) View.VISIBLE else View.GONE
            label.visibility = visible
            if (!enabled || players.isEmpty()) {
                spinner.visibility = View.GONE
            } else {
                spinner.visibility = View.VISIBLE
            }
            empty.visibility = if (enabled && players.isEmpty()) View.VISIBLE else View.GONE
        }

        fun paintHttpState(allowed: Boolean) {
            httpState.setText(if (allowed) R.string.settings_http_on else R.string.settings_http_off)
        }

        var players: List<com.tvplayer.app.engine.ExternalPlayerInfo> = emptyList()
        var loading = true
        lifecycleScope.launch {
            policyRefresh?.join() // SECCION-06: currentPolicy ya es fresco aquí
            val dao = container.db.settingsDao()
            global.isChecked = container.currentPolicy.externalGlobal
            // Init desde DAO fresco (no currentPolicy): una escritura directa
            // al DAO sin refreshNetPolicy se refleja igual (SECCION-02).
            val fresh = dao.get(AppContainer.KEY_ALLOW_HTTP, "false") == "true"
            allowHttp.isChecked = fresh
            paintHttpState(fresh)
            players = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.tvplayer.app.engine.ExternalPlayers.listVideoPlayers(requireContext())
            }
            val saved = com.tvplayer.app.engine.ExternalPlayers.getPreferred(
                dao, AppContainer.KEY_EXTERNAL_PLAYER_PACKAGE,
            )
            spinner.adapter = ExternalPlayerAdapter(requireContext(), players)
            spinner.setSelection(indexOfPackage(players, saved))
            paintSelector(global.isChecked, players)
            loading = false
        }
        allowHttp.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch {
                container.db.settingsDao().put(AppContainer.KEY_ALLOW_HTTP, checked.toString())
                container.refreshNetPolicy()
                paintHttpState(checked)
            }
        }
        global.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch {
                container.db.settingsDao().put(AppContainer.KEY_EXTERNAL_GLOBAL, checked.toString())
                container.refreshNetPolicy()
                paintSelector(checked, players)
            }
        }
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long,
            ) {
                if (loading) return
                lifecycleScope.launch {
                    com.tvplayer.app.engine.ExternalPlayers.setPreferred(
                        container.db.settingsDao(),
                        AppContainer.KEY_EXTERNAL_PLAYER_PACKAGE,
                        packageAtPosition(players, position),
                    )
                    container.refreshNetPolicy()
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    /** Posición 0 = "Preguntar siempre" (null); el resto sigue [players]. */
    private fun indexOfPackage(
        players: List<com.tvplayer.app.engine.ExternalPlayerInfo>,
        saved: String?,
    ): Int {
        if (saved.isNullOrBlank()) return 0
        val i = players.indexOfFirst { it.packageName == saved }
        return if (i < 0) 0 else i + 1
    }

    private fun packageAtPosition(
        players: List<com.tvplayer.app.engine.ExternalPlayerInfo>,
        position: Int,
    ): String? = if (position <= 0) null else players.getOrNull(position - 1)?.packageName

    /** Filas del selector: "Preguntar siempre" + icono+nombre por app. */
    private class ExternalPlayerAdapter(
        context: android.content.Context,
        private val players: List<com.tvplayer.app.engine.ExternalPlayerInfo>,
    ) : android.widget.BaseAdapter() {
        private val inflater = android.view.LayoutInflater.from(context)
        private val askText = context.getString(R.string.settings_external_ask)
        private val density = context.resources.displayMetrics.density

        override fun getCount(): Int = players.size + 1
        override fun getItem(position: Int): Any? = null
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View =
            row(position, convertView, parent, compact = true)

        override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View =
            row(position, convertView, parent, compact = false)

        private fun row(
            position: Int,
            convertView: View?,
            parent: android.view.ViewGroup?,
            compact: Boolean,
        ): View {
            val root = (convertView as? android.widget.LinearLayout) ?: android.widget.LinearLayout(
                inflater.context,
            ).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val pad = (12 * density).toInt()
                setPadding(pad, if (compact) pad / 2 else pad, pad, if (compact) pad / 2 else pad)
                addView(android.widget.ImageView(context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        (24 * density).toInt(), (24 * density).toInt(),
                    ).apply { marginEnd = (12 * density).toInt() }
                })
                addView(TextView(context).apply {
                    setTextColor(context.getColor(R.color.tp_text))
                    textSize = 15f
                })
            }
            val icon = root.getChildAt(0) as android.widget.ImageView
            val text = root.getChildAt(1) as TextView
            if (position == 0) {
                icon.setImageDrawable(null)
                icon.visibility = View.INVISIBLE
                text.text = askText
            } else {
                val info = players[position - 1]
                icon.visibility = View.VISIBLE
                icon.setImageDrawable(info.icon)
                icon.contentDescription = info.label
                text.text = info.label
            }
            return root
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
            policyRefresh?.join() // SECCION-06: currentPolicy ya es fresco aquí
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
                val ok = persistProxy(container, enable.isChecked, host.text.toString(), port.text.toString(),
                    if (type.selectedItemPosition == 1) "SOCKS" else "HTTP",
                    user.text.toString(), pass.text.toString())
                pass.text.clear()
                Toast.makeText(
                    requireContext(),
                    if (ok) R.string.settings_proxy_saved else R.string.settings_proxy_invalid,
                    if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                ).show()
            }
        }
        testBtn.setOnClickListener {
            result.visibility = View.VISIBLE
            result.text = getString(R.string.lists_refreshing)
            lifecycleScope.launch {
                val ok = persistProxy(container, enable.isChecked, host.text.toString(), port.text.toString(),
                    if (type.selectedItemPosition == 1) "SOCKS" else "HTTP",
                    user.text.toString(), pass.text.toString())
                if (!ok) {
                    result.text = getString(R.string.settings_proxy_error, getString(R.string.settings_proxy_invalid))
                    return@launch
                }
                val url = testUrl.text.toString().trim()
                if (!com.tvplayer.app.data.net.ProxyProbeGate.isProbeAllowed(url, container.allowHttp)) {
                    result.text = probeBlockedMessage(url)
                    return@launch
                }
                result.text = probeUrl(container, url)
            }
        }
    }

    /**
     * LOW 1: valida antes de persistir. Si se pide habilitar con host vacío
     * o puerto fuera de 1-65535, NO habilita (proxy_enabled=false) y devuelve
     * false para que el llamador avise con mensaje accionable. Nunca deja
     * una config inválida habilitada (evita fail-open a DIRECT en silencio
     * en AppProxySelector) ni reconstruye el transporte con ella.
     */
    private suspend fun persistProxy(
        container: AppContainer,
        enabled: Boolean,
        host: String,
        port: String,
        type: String,
        user: String,
        pass: String,
    ): Boolean {
        val dao = container.db.settingsDao()
        val trimmedHost = host.trim()
        val parsedPort = com.tvplayer.app.data.net.ProxyConfig.parsePort(port)
        val effectiveEnabled = enabled && parsedPort != null && trimmedHost.isNotEmpty()
        dao.put(com.tvplayer.app.data.net.ProxyConfig.KEY_ENABLED, effectiveEnabled.toString())
        dao.put(com.tvplayer.app.data.net.ProxyConfig.KEY_HOST, trimmedHost)
        dao.put(com.tvplayer.app.data.net.ProxyConfig.KEY_PORT, (parsedPort ?: 8080).toString())
        dao.put(com.tvplayer.app.data.net.ProxyConfig.KEY_TYPE, type)
        val u = user.trim()
        if (u.isNotEmpty() || pass.isNotEmpty()) {
            runCatching { container.proxyCreds.save(u, pass) }
        } else if (u.isEmpty()) {
            runCatching { container.proxyCreds.clear() }
        }
        container.refreshNetPolicy()
        runCatching { coil.Coil.setImageLoader(container.imageLoader) }
        return !enabled || effectiveEnabled
    }

    /** Mensaje accionable cuando la puerta del probe bloquea, sin tocar red. */
    private fun probeBlockedMessage(url: String): String {
        val reason = if (!com.tvplayer.app.core.UrlPolicer.isAllowedHttpUrl(url)) {
            getString(R.string.settings_proxy_invalid_url)
        } else {
            getString(R.string.error_cleartext)
        }
        return getString(R.string.settings_proxy_error, reason)
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

    /**
     * GET con timeout de 8 s. Devuelve el mensaje a mostrar; nunca loguea
     * credenciales. LOW 2: la puerta del probe ([ProxyProbeGate]) va ANTES
     * de cualquier petición — URL no permitida o http con cleartext
     * bloqueado devuelven el error sin tocar red.
     */
    private suspend fun probeUrl(container: AppContainer, url: String): String {
        if (!com.tvplayer.app.data.net.ProxyProbeGate.isProbeAllowed(url, container.allowHttp)) {
            return probeBlockedMessage(url)
        }
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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

    /** Limpieza del diskCache de Coil (ExperimentalCoilApi: opt-in solo aquí). */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    private fun clearCoilDiskCache(loader: coil.ImageLoader) {
        runCatching {
            loader.diskCache?.directory?.let { dir ->
                java.io.File(dir.toString()).deleteRecursively()
            }
        }
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
