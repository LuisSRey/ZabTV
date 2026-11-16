package com.zabtv.app.data.repo

import com.zabtv.app.core.Normalizer
import com.zabtv.app.data.db.ChannelEntity
import com.zabtv.app.data.db.EpgDao
import com.zabtv.app.data.db.EpgProgramEntity

/**
 * Resuelve la clave EPG de un canal (Task 14): exacta por tvg-id o aproximada
 * por nombre normalizado contra el catálogo XMLTV (fallback marcado con la
 * etiqueta "aproximado" en UI). Nunca inventa guía: sin programas → null.
 */
class EpgMatcher(private val epgDao: EpgDao) {

    /** null = este canal no tiene guía (ni exacta ni aproximada). */
    suspend fun match(channel: ChannelEntity): EpgMatch? {
        val tvgId = channel.tvgId
        if (!tvgId.isNullOrBlank() && epgDao.hasPrograms(tvgId)) {
            return EpgMatch(tvgId, approximate = false)
        }
        val norm = Normalizer.normName(channel.displayName)
        if (norm.isBlank()) return null
        val candidates = epgDao.allEpgChannels()
        val hit = candidates.firstOrNull { candidate ->
            candidate.tvgId != tvgId &&
                candidate.displayName?.isNotBlank() == true &&
                fuzzyEquals(norm, Normalizer.normName(candidate.displayName))
        } ?: return null
        if (!epgDao.hasPrograms(hit.tvgId)) return null
        return EpgMatch(hit.tvgId, approximate = true)
    }

    suspend fun dayPrograms(tvgId: String, startInclMillis: Long, endInclMillis: Long): List<EpgProgramEntity> =
        epgDao.dayPrograms(tvgId, startInclMillis, endInclMillis)

    suspend fun nowNext(tvgIds: List<String>, atMillis: Long) =
        if (tvgIds.isEmpty()) emptyMap() else epgDao.nowNext(tvgIds, atMillis)
}

/** Clave EPG resuelta + si es fallback por nombre (etiqueta "aproximado"). */
data class EpgMatch(val tvgId: String, val approximate: Boolean)

/**
 * Igualdad exacta o subconjunto de tokens (para prefijos de proveedor tipo
 * "ES | Telecinco" ↔ "Telecinco"). El lado corto debe tener presencia real
 * (≥6 letras) para no colgar nombres genéricos ("Canal" → "Canal 2").
 */
internal fun fuzzyEquals(a: String, b: String): Boolean {
    if (a == b) return true
    val short = if (a.length <= b.length) a else b
    val long = if (a.length <= b.length) b else a
    if (short.replace(" ", "").length < 6) return false
    val longTokens = long.split(' ').toSet()
    return short.split(' ').all { it in longTokens }
}
