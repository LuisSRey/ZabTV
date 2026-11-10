package com.tvplayer.app.data.net

import java.net.URI

/**
 * DNS-over-HTTPS a nivel de app (S2). El DNS se aplica al ÚNICO OkHttp
 * compartido vía `OkHttpClient.dns()` en [com.tvplayer.app.data.AppContainer.buildOkHttp]:
 * import, EPG, logos y playback resuelven por el mismo proveedor.
 *
 * Presets RFC 8484 (GET `?dns=`):
 * - Cloudflare → https://1.1.1.1/dns-query (bootstrap 1.1.1.1, 1.0.0.1)
 * - Google → https://dns.google/dns-query (bootstrap 8.8.8.8, 8.8.4.4).
 *
 * ADAPTACIÓN DOCUMENTADA: el diseño pedía Google como
 * `https://8.8.8.8/resolve`, pero ese endpoint es la API JSON de Google,
 * incompatible con `okhttp-dnsoverhttps` (RFC 8484 wire-format). Se usa el
 * endpoint RFC 8484 oficial de Google (`https://dns.google/dns-query`),
 * equivalente funcional: mismo operador, mismo cifrado, sin JSON.
 *
 * Custom inválida (no https con host) → [System] (fallback + toast en Settings).
 */
sealed interface DohConfig {

    data object System : DohConfig
    data object Cloudflare : DohConfig
    data object Google : DohConfig
    data class Custom(val url: String) : DohConfig

    companion object {
        const val KEY_MODE = "doh_mode"
        const val KEY_CUSTOM_URL = "doh_custom_url"

        const val MODE_SYSTEM = "system"
        const val MODE_CLOUDFLARE = "cloudflare"
        const val MODE_GOOGLE = "google"
        const val MODE_CUSTOM = "custom"

        fun from(mode: String?, customUrl: String?): DohConfig = when (mode) {
            MODE_CLOUDFLARE -> Cloudflare
            MODE_GOOGLE -> Google
            MODE_CUSTOM -> {
                val u = customUrl?.trim().orEmpty()
                if (isValidHttpsUrl(u)) Custom(u) else System
            }
            else -> System
        }

        fun modeOf(config: DohConfig): String = when (config) {
            is System -> MODE_SYSTEM
            is Cloudflare -> MODE_CLOUDFLARE
            is Google -> MODE_GOOGLE
            is Custom -> MODE_CUSTOM
        }

        /** Solo https con host, sin user-info ni espacios. */
        fun isValidHttpsUrl(url: String): Boolean {
            if (url.isBlank() || url.any { it.isWhitespace() || it.code < 0x20 }) return false
            return try {
                val uri = URI(url.trim())
                uri.scheme.equals("https", ignoreCase = true) &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null
            } catch (_: Exception) {
                false
            }
        }
    }
}
