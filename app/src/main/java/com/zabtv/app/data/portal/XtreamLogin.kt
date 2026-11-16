package com.zabtv.app.data.portal

import java.net.URI
import java.net.URLEncoder

/**
 * Portal Xtream Codes (Task 12): construye las URLs de playlist M3U y EPG a
 * partir de host/usuario/password y delega la descarga en ImportService (Task 6).
 * Lógica pura sin Android — testeable en JVM.
 *
 * Nunca se logean las credenciales (spec): los callers deben sanear mensajes
 * que contengan la URL construida antes de mostrarlos.
 */
object XtreamLogin {

    /** Host normalizado con esquema (https:// por defecto) y sin barra final. */
    fun normalizeHost(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        require(trimmed.isNotEmpty()) { "host de portal vacío" }
        require(trimmed.none { it.isWhitespace() }) { "host de portal inválido: $raw" }
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = try {
            URI(withScheme)
        } catch (_: Exception) {
            throw IllegalArgumentException("host de portal inválido: $raw")
        }
        val host = uri.host
        require(!host.isNullOrBlank()) { "host de portal inválido: $raw" }
        return withScheme.trimEnd('/')
    }

    /** get.php?username=U&password=P&type=m3u_plus&output=ts (spec del plan). */
    fun playlistUrl(host: String, username: String, password: String): String {
        val base = normalizeHost(host)
        return "$base/get.php?username=${enc(username)}&password=${enc(password)}&type=m3u_plus&output=ts"
    }

    /** xmltv.php?username=U&password=P (spec del plan). */
    fun epgUrl(host: String, username: String, password: String): String {
        val base = normalizeHost(host)
        return "$base/xmltv.php?username=${enc(username)}&password=${enc(password)}"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
