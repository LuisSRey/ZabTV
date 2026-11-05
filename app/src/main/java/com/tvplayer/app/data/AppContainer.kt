package com.tvplayer.app.data

import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import coil.disk.DiskCache
import com.tvplayer.app.data.db.AppDatabase
import com.tvplayer.app.data.net.CleartextGate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * DI manual (spec §3 — decisión de stack). Un solo OkHttp compartido por TODO el
 * transporte de la app (import, EPG, logos vía Coil, playback vía datasource de
 * Media3): aquí vive el único punto de bloqueo de texto-plano (CleartextGate, Task 11).
 */
class AppContainer(private val appContext: Context) {

    val db: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "tvplayer.db")
            .build()
    }

    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(cleartextGate)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS) // streams largos: sin cap de llamada aquí (los caps van por caso de uso)
            .followRedirects(true)
            .build()
    }

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

    suspend fun refreshCleartextPolicy() {
        allowHttp = db.settingsDao().get(KEY_ALLOW_HTTP, "false") == "true"
    }

    fun setAllowHttp(value: Boolean) {
        allowHttp = value
    }

    companion object {
        const val KEY_ALLOW_HTTP = "allow_http"
    }

    /**
     * ImageLoader de Coil 2.7 sobre el MISMO OkHttp compartido (spec F8): los
     * logos nunca usan un cliente paralelo sin el gate. TpApp lo instala como
     * singleton global (`Coil.setImageLoader`) en onCreate, así cada `load()`
     * de la UI atraviesa el CleartextGate. Presupuesto en disco del spec: 96 MB.
     */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .okHttpClient(okHttp)
            .crossfade(120)
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(96L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
}