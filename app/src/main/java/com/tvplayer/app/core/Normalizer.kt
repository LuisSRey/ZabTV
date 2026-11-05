package com.tvplayer.app.core

import java.text.Normalizer
import java.util.Locale

/**
 * Normalización de nombres de canal (spec F5/F6): matching indexado y tolerante.
 * Ruling: '-' y '_' se convierten en espacio para igualar nombres estilo
 * "ÁTV-2" ↔ "ATV 2" en el fallback léxica de la EPG.
 */
object Normalizer {

    private val QUALITY_TOKENS = setOf(
        "hd", "fhd", "uhd", "sd", "4k", "8k", "1080p", "720p", "1080i", "720i",
    )

    fun normName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = raw.lowercase(Locale.ROOT)
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        s = s.map { ch -> if (ch.isLetterOrDigit() || ch.isWhitespace() || ch in "-_") ch else ' ' }
            .joinToString("")
        s = s.replace('-', ' ').replace('_', ' ')
        return s.split(' ').filter { it.isNotEmpty() && it !in QUALITY_TOKENS }.joinToString(" ").trim()
    }
}
