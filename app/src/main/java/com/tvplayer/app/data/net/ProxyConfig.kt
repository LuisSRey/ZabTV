package com.tvplayer.app.data.net

/**
 * Proxy HTTP/SOCKS opcional (S1). Todo el tráfico de red de la app pasa por
 * el ÚNICO OkHttp compartido de AppContainer, así que el proxy se aplica en
 * un solo punto (ProxySelector) a import, EPG, logos y playback.
 *
 * Loopback (127.0.0.1/localhost/::1) NUNCA va al proxy: es el motor AceStream
 * local y los bucles de prueba.
 */
enum class ProxyType { HTTP, SOCKS }

data class ProxyConfig(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 8080,
    val type: ProxyType = ProxyType.HTTP,
) {
    companion object {
        const val KEY_ENABLED = "proxy_enabled"
        const val KEY_HOST = "proxy_host"
        const val KEY_PORT = "proxy_port"
        const val KEY_TYPE = "proxy_type"
    }
}
