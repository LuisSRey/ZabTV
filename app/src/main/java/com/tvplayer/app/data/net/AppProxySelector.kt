package com.tvplayer.app.data.net

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Selector de proxy de la app (S1). OFF o host vacío → DIRECT siempre.
 * ON → DIRECT solo para loopback (motor AceStream local); el resto va al
 * proxy HTTP o SOCKS configurado.
 */
class AppProxySelector(private val config: ProxyConfig) : ProxySelector() {

    override fun select(uri: URI?): List<Proxy> {
        if (uri == null || !config.enabled) return listOf(Proxy.NO_PROXY)
        val host = config.host.trim()
        if (host.isEmpty()) return listOf(Proxy.NO_PROXY)
        if (isLoopback(uri.host)) return listOf(Proxy.NO_PROXY)
        val type = when (config.type) {
            ProxyType.HTTP -> Proxy.Type.HTTP
            ProxyType.SOCKS -> Proxy.Type.SOCKS
        }
        return try {
            listOf(Proxy(type, InetSocketAddress(host, config.port)))
        } catch (_: IllegalArgumentException) {
            listOf(Proxy.NO_PROXY)
        }
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        // Sin reintento alternativo: el error de red lo reporta el llamador.
    }

    companion object {
        /** Loopback en las formas que devuelven URI.getHost() y HttpUrl.host(). */
        fun isLoopback(host: String?): Boolean {
            if (host.isNullOrBlank()) return false
            val h = host.trim().lowercase().removePrefix("[").removeSuffix("]")
            return h == "localhost" || h == "127.0.0.1" || h == "::1" ||
                h == "0:0:0:0:0:0:0:1"
        }
    }
}
