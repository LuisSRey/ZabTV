package com.tvplayer.app.data.net

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Único punto que permite/bloquea http:// en toda la app (spec F8, Task 11).
 * Instalado en el OkHttp compartido de AppContainer: import, EPG, logos (Coil)
 * y playback (Media3) pasan por aquí. https:// siempre pasa.
 *
 * `allowHttp` es un proveedor para no tocar Room en el hilo de red: AppContainer
 * lo respalda con un flag volátil sincronizado con SettingsDao ("allow_http").
 */
class CleartextGate(private val allowHttp: () -> Boolean) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.scheme.equals("http", ignoreCase = true) && !allowHttp()) {
            throw IOException(
                "HTTP sin cifrar bloqueado. Actívalo en Ajustes o usa una lista https://",
            )
        }
        return chain.proceed(request)
    }
}
