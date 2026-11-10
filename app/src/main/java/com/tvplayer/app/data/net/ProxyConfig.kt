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

        /** Puerto TCP válido: 1-65535. Fuera de rango = config inválida. */
        fun isValidPort(port: Int): Boolean = port in 1..65535

        /**
         * Parsea el puerto del campo de texto. null = inválido (vacío,
         * no numérico o fuera de 1-65535): el llamador NO debe habilitar
         * el proxy con este valor (evita fail-open a DIRECT en silencio).
         */
        fun parsePort(raw: String?): Int? {
            val p = raw?.trim()?.toIntOrNull() ?: return null
            return if (isValidPort(p)) p else null
        }

        /** Config habilitable: host no vacío y puerto en rango. */
        fun isValid(host: String, port: Int): Boolean =
            host.trim().isNotEmpty() && isValidPort(port)
    }
}
