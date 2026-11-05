package com.tvplayer.app.engine

import com.tvplayer.app.core.UrlPolicer

/**
 * Fuente de reproducción (Task 8). El tipo se resuelve por extensión de URL y, de
 * faltar, por Content-Type de la respuesta HTTP. Esquemas no http/https (acestream://,
 * rtmp://, udp://...) y http:// con cleartext desactivado se rechazan ANTES de tocar
 * el player (spec F8, §6.8).
 */
sealed interface PlaybackSource {

    data class Hls(val url: String) : PlaybackSource
    data class Dash(val url: String) : PlaybackSource
    data class Progressive(val url: String) : PlaybackSource

    companion object {

        private val EXT_HLS = setOf("m3u8", "m3u")
        private val EXT_DASH = setOf("mpd")
        private val EXT_PROG = setOf("ts", "mp4", "mkv", "mov", "avi", "flv", "wmv")

        /** type puede ser "application/vnd.apple.mpegurl", "application/dash+xml", etc. */
        fun forUrl(
            url: String?,
            contentType: String? = null,
            allowCleartext: Boolean = false,
            aceStreamProxy: String? = null,
        ): PlaybackSource {
            val raw = url?.trim() ?: throw IllegalArgumentException("URL de canal nula")
            if (raw.startsWith("acestream://", ignoreCase = true)) {
                return aceSource(raw, aceStreamProxy)
            }
            val checked = raw
            if (!UrlPolicer.isAllowedHttpUrl(checked)) {
                throw IllegalArgumentException("URL de canal no permitida: ${checked.take(80)}")
            }
            // Loopback siempre permitido (proxy AceStream local), como la plataforma.
            val isLoopback = try {
                val host = java.net.URI(checked).host?.lowercase().orEmpty()
                host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host == "::1"
            } catch (_: Exception) {
                false
            }
            if (checked.startsWith("http://", ignoreCase = true) && !allowCleartext && !isLoopback) {
                throw IllegalArgumentException(
                    "Texto plano desactivado: active 'Permitir HTTP' en Ajustes o use https://",
                )
            }

            return when {
                contentType != null -> byContentType(checked, contentType)
                else -> byExtension(checked)
            }
        }

        /**
         * acestream://<infohash> → proxy HTTP local del motor (app AceStream en
         * 127.0.0.1:6878 por defecto). Sin proxy configurado se rechaza con
         * mensaje accionable ANTES de crear el player.
         */
        private fun aceSource(raw: String, aceStreamProxy: String?): PlaybackSource {
            val hash = raw.substringAfter("://").trim()
            require(hash.isNotEmpty() && hash.none { it.isWhitespace() }) {
                "URL de canal no permitida: acestream inválido"
            }
            val proxy = aceStreamProxy?.trim().orEmpty()
            if (proxy.isEmpty()) {
                throw IllegalArgumentException(
                    "AceStream necesita el motor local: instala la app AceStream y configura el proxy en Ajustes",
                )
            }
            val host = proxy.removePrefix("http://").removePrefix("https://").trimEnd('/')
            require(host.isNotEmpty()) { "AceStream: proxy inválido en Ajustes" }
            return Progressive("http://$host/ace/getstream?id=$hash")
        }

        private fun byContentType(url: String, ct: String): PlaybackSource {
            val lower = ct.lowercase().substringBefore(';').trim()
            return when {
                lower.contains("mpegurl") -> Hls(url)
                lower.contains("dash+xml") -> Dash(url)
                else -> Progressive(url)
            }
        }

        private fun byExtension(url: String): PlaybackSource {
            val ext = url.substringAfterLast('.').lowercase().takeWhile { it.isLetterOrDigit() }
            return when {
                ext in EXT_HLS -> Hls(url)
                ext in EXT_DASH -> Dash(url)
                else -> Progressive(url)
            }
        }
    }
}

/** Resultado tipificado del mapeo de errores de Media3 (Task 8). */
data class PlayerError(
    val code: Code,
    val userMessageRes: Int,
) {
    enum class Code { NETWORK, SERVER, UNSUPPORTED_FORMAT, CLEARTEXT_BLOCKED, GENERIC }
}
