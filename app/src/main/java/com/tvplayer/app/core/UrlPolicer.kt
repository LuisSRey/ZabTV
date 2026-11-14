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
        if (!isAllowedHttpUrl(url)) throw UrlPolicyException("URL no permitida: ${describeForError(url)}")
        return url!!
    }

    /**
     * T-F2 (security B-2): descriptor sanitizado para el mensaje de rechazo —
     * SOLO esquema+host, NUNCA user-info ni URL completa (el mensaje llega al
     * toast verbatim vía ImportService→safeMessage). Con credenciales
     * incrustadas el mensaje lo dice explícito para seguir accionable.
     * isAllowedHttpUrl NO se relaja: el user-info sigue vetado.
     */
    private fun describeForError(url: String?): String {
        if (url.isNullOrBlank()) return "(vacía)"
        return try {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return "(ilegible)"
            val host = uri.host ?: return "(ilegible)"
            val port = if (uri.port > 0) ":${uri.port}" else ""
            val base = "$scheme://$host$port"
            if (uri.userInfo != null) "$base (la URL trae credenciales incrustadas: quítalas de la URL)"
            else "$base (esquema/contenido no autorizado)"
        } catch (_: URISyntaxException) {
            "(ilegible)"
        }
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
