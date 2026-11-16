package com.zabtv.app.core

import java.security.MessageDigest

/**
 * Clave estable del canal (spec F4): tvg-id si existe; si no, sha1 de
 * normName+group. La usan ImportService (upsert idempotente) y Favourites —
 * favorito sobrevive a reimportaciones con la misma identidad de canal.
 */
object StableKey {

    fun of(tvgId: String?, name: String, group: String): String {
        if (!tvgId.isNullOrBlank()) return tvgId
        val digest = MessageDigest.getInstance("SHA-1")
        val bytes = digest.digest("${Normalizer.normName(name)}|$group".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
