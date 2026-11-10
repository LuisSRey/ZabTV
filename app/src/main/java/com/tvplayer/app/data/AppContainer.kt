package com.tvplayer.app.data

import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import coil.disk.DiskCache
import com.tvplayer.app.data.db.AppDatabase
import com.tvplayer.app.data.net.AppProxySelector
import com.tvplayer.app.data.net.CleartextGate
import com.tvplayer.app.data.net.DohConfig
import com.tvplayer.app.data.net.DohProvider
import com.tvplayer.app.data.net.ProxyConfig
import com.tvplayer.app.data.net.ProxyCreds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.net.ProxySelector
import java.util.concurrent.TimeUnit

/**
 * DI manual (spec §3 — decisión de stack). Un solo OkHttp compartido por TODO el
 * transporte de la app (import, EPG, logos vía Coil, playback vía datasource de
 * Media3): aquí vive el único punto de bloqueo de texto-plano (CleartextGate, Task 11).
 *
 * S1: el cliente es un `@Volatile var` reconstruido por [rebuildOkHttp] cuando
 * cambia la [NetPolicy] (cleartext, proxy, DoH, externo global). El orden de
 * interceptores es contractual: CleartextGate SIEMPRE en [0] (CleartextGateOrderTest).
 */
class AppContainer(private val appContext: Context) {

    val db: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "tvplayer.db")
            .addMigrations(com.tvplayer.app.data.db.MIGRATION_1_2)
            .build()
    }

    /** Política de red efectiva (SettingsDao + ProxyCreds). Ver [refreshNetPolicy]. */
    data class NetPolicy(
        val allowHttp: Boolean = false,
        val proxy: ProxyConfig = ProxyConfig(),
        val doh: DohConfig = DohConfig.System,
    )

    @Volatile
    var currentPolicy: NetPolicy = NetPolicy()
        private set

    /**
     * Política de cleartext (Task 11, spec F8). Flag volátil respaldado por
     * SettingsDao ("allow_http", default OFF): el interceptor lo lee sin tocar
     * Room en el hilo de red. Se carga al arrancar (TpApp) y al cambiar el
     * ajuste (SettingsFragment).
     */
    @Volatile
    var allowHttp: Boolean = false
        private set

    val cleartextGate = CleartextGate { allowHttp }

    val proxyCreds: ProxyCreds by lazy { ProxyCreds.createEncrypted(appContext) }

    /** Único OkHttp de la app. Se reconstruye con [rebuildOkHttp] al cambiar la política. */
    @Volatile
    var okHttp: OkHttpClient = buildOkHttp(currentPolicy, auth = null)
        private set

    /**
     * Construye el cliente con la política dada. Contrato: CleartextGate es
     * SIEMPRE el interceptor[0] (la política de texto-plano se evalúa antes
     * que proxy/DNS). `auth` son las credenciales del proxy (Proxy-Authorization);
     * null = sin autenticación de proxy.
     */
    fun buildOkHttp(policy: NetPolicy, auth: Pair<String, String>?): OkHttpClient {
        val proxySelector = selectProxy(policy)
        // Bootstrap SIN dns propio para las consultas DoH (evita recursión);
        // hereda proxy + gate para no fugar DNS fuera del túnel.
        val bootstrap = OkHttpClient.Builder()
            .addInterceptor(cleartextGate)
            .proxySelector(proxySelector)
            .build()
        val builder = OkHttpClient.Builder()
            .addInterceptor(cleartextGate)
            .proxySelector(proxySelector)
            .dns(DohProvider.buildDns(policy.doh, bootstrap))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS) // streams largos: sin cap de llamada aquí (los caps van por caso de uso)
            .followRedirects(true)
        if (auth != null) {
            builder.proxyAuthenticator { _, response ->
                if (response.request.header("Proxy-Authorization") != null) return@proxyAuthenticator null
                response.request.newBuilder()
                    .header("Proxy-Authorization", okhttp3.Credentials.basic(auth.first, auth.second))
                    .build()
            }
        }
        return builder.build()
    }

    private fun selectProxy(policy: NetPolicy): ProxySelector =
        if (policy.proxy.enabled && policy.proxy.host.trim().isNotEmpty()) {
            AppProxySelector(policy.proxy)
        } else {
            ProxySelector.getDefault()
        }

    /** Reconstruye el cliente compartido (y el ImageLoader que lo envuelve) con la política en vigor. */
    fun rebuildOkHttp(auth: Pair<String, String>? = null) {
        okHttp = buildOkHttp(currentPolicy, auth)
        imageLoader = buildImageLoader(okHttp)
    }

    /** Lee SettingsDao (keys proxy_* + allow_http) y ProxyCreds, y reconstruye el transporte. */
    suspend fun refreshNetPolicy() {
        val dao = db.settingsDao()
        val allow = dao.get(KEY_ALLOW_HTTP, "false") == "true"
        val proxy = ProxyConfig(
            enabled = dao.get(ProxyConfig.KEY_ENABLED, "false") == "true",
            host = dao.get(ProxyConfig.KEY_HOST, "").orEmpty(),
            port = dao.get(ProxyConfig.KEY_PORT, "8080")?.toIntOrNull() ?: 8080,
            type = if (dao.get(ProxyConfig.KEY_TYPE, "HTTP") == "SOCKS") {
                com.tvplayer.app.data.net.ProxyType.SOCKS
            } else {
                com.tvplayer.app.data.net.ProxyType.HTTP
            },
        )
        val auth = runCatching { proxyCreds.load() }.getOrNull()
        val doh = DohConfig.from(
            dao.get(DohConfig.KEY_MODE, DohConfig.MODE_SYSTEM),
            dao.get(DohConfig.KEY_CUSTOM_URL, ""),
        )
        allowHttp = allow
        currentPolicy = NetPolicy(allowHttp = allow, proxy = proxy, doh = doh)
        rebuildOkHttp(auth)
    }

    suspend fun refreshCleartextPolicy() {
        refreshNetPolicy()
    }

    fun setAllowHttp(value: Boolean) {
        allowHttp = value
    }

    companion object {
        const val KEY_ALLOW_HTTP = "allow_http"
        const val KEY_ACE_PROXY = "acestream_proxy"
        /** Proxy del motor AceStream (app AceStream en el propio dispositivo). */
        const val DEFAULT_ACE_PROXY = "127.0.0.1:6878"
    }

    private fun buildImageLoader(client: OkHttpClient): ImageLoader =
        ImageLoader.Builder(appContext)
            .okHttpClient(client)
            .crossfade(120)
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(96L * 1024 * 1024)
                    .build()
            }
            .build()

    /**
     * ImageLoader de Coil 2.7 sobre el MISMO OkHttp compartido (spec F8): los
     * logos nunca usan un cliente paralelo sin el gate. TpApp lo instala como
     * singleton global (`Coil.setImageLoader`) en onCreate, así cada `load()`
     * de la UI atraviesa el CleartextGate. Presupuesto en disco del spec: 96 MB.
     * Se reconstruye junto al cliente en [rebuildOkHttp].
     */
    var imageLoader: ImageLoader = buildImageLoader(okHttp)
        private set

    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
}
