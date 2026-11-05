package com.tvplayer.app.data.repo

import com.tvplayer.app.core.Normalizer
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.EpgDao
import com.tvplayer.app.data.db.EpgProgramEntity

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
                Normalizer.normName(candidate.displayName.orEmpty()) == norm &&
                candidate.displayName?.isNotBlank() == true
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
