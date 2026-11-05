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
        ): PlaybackSource {
            val url = url ?: throw IllegalArgumentException("URL de canal nula")
            if (!UrlPolicer.isAllowedHttpUrl(url)) {
                throw IllegalArgumentException("URL de canal no permitida: ${url.take(80)}")
            }
            if (url.startsWith("http://", ignoreCase = true) && !allowCleartext) {
                throw IllegalArgumentException(
                    "Texto plano desactivado: active 'Permitir HTTP' en Ajustes o use https://",
                )
            }

            return when {
                contentType != null -> byContentType(url, contentType)
                else -> byExtension(url)
            }
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
