package com.tvplayer.app.core

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Allowlist de esquemas del spec (§6-F8): media y logos SOLO http(s);
 * el resto se rechaza ANTES de tocar red o decoder. Nada de user-info,
 * espacios o caracteres de control.
 */
object UrlPolicer {

    fun isAllowedHttpUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (url.any { it.isWhitespace() || it.code < 0x20 }) return false
        val uri = try {
            URI(url.trim())
        } catch (_: URISyntaxException) {
            return false
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host ?: return false
        if (host.isBlank()) return false
        if (uri.userInfo != null) return false
        return true
    }

    fun requireAllowedHttpUrl(url: String?): String {
        if (!isAllowedHttpUrl(url)) throw UrlPolicyException("URL no permitida (esquema/contenido no autorizado): ${url ?: "null"}")
        return url!!
    }

    /**
     * SECCION-01 (D3): true si la URL trae user-info (user:pass@). Es la
     * descomposición de !isAllowedHttpUrl que permite clasificar la causa:
     * URL con user-info → veto USER_INFO (mensaje de seguridad), el resto de
     * rechazos → BAD_SCHEME. Nula/blank/ilegible → false (será BAD_SCHEME).
     */
    fun hasUserInfo(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = try {
            URI(url.trim())
        } catch (_: URISyntaxException) {
            return false
        }
        return uri.userInfo != null
    }
}

class UrlPolicyException(message: String) : RuntimeException(message)
