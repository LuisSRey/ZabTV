package com.tvplayer.app

import android.app.Application
import coil.Coil
import com.tvplayer.app.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TpApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Logos SIEMPRE por el único transporte (OkHttp compartido + CleartextGate, spec F8)
        Coil.setImageLoader(container.imageLoader)
        // Política de cleartext desde SettingsDao (default OFF) al hilo IO
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { container.refreshCleartextPolicy() }
        }
        // EPG periódica cada 6 h (Task 13; KEEP para no duplicar)
        runCatching { com.tvplayer.app.data.work.EpgWorker.schedule(this) }
    }
}
