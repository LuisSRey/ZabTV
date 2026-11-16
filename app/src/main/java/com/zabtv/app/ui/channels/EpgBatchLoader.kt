package com.zabtv.app.ui.channels

import com.zabtv.app.core.xmltv.GuideWindow
import com.zabtv.app.data.db.ChannelEntity
import com.zabtv.app.data.db.EpgDao
import com.zabtv.app.data.db.EpgProgramEntity
import com.zabtv.app.data.repo.EpgMatcher

/**
 * Lote EPG con caché por ventana (S4): la primera carga de un anchor va a
 * DB + matcher y acumula la unión en [EpgWindowCache] (cap 1); las
 * siguientes con el mismo anchor reutilizan lo cubierto y solo consultan
 * los tvgIds nuevos. Full-hit (todo cubierto, incluidos los negativos) →
 * cero queries y cero llamadas al matcher.
 *
 * La ingesta/matching no cambian: el fallback aproximado sigue siendo SOLO
 * en misses exactos ([BrowserEpgResolver]), acotado a los visibles.
 */
class EpgBatchLoader(
    private val epgDao: EpgDao,
    private val timelineOf: suspend (List<String>, GuideWindow) -> Map<String, List<EpgProgramEntity>>,
) {

    data class Result(
        val nn: Map<String, Pair<EpgProgramEntity?, EpgProgramEntity?>>,
        val range: Map<String, List<EpgProgramEntity>>,
        val approx: Map<String, String>,
        /** true = esta carga no ejecutó ningún query ni matcher. */
        val cacheHit: Boolean,
        /** match() ejecutados en esta carga. */
        val matcherCalls: Int,
    )

    suspend fun load(
        visibles: List<ChannelEntity>,
        window: GuideWindow,
        now: Long,
    ): Result {
        val anchor = window.anchorMillis
        val cached = EpgWindowCache.get(anchor)
        val nn = cached?.nn.orEmpty().toMutableMap()
        val range = cached?.range.orEmpty().toMutableMap()
        val approx = cached?.approx.orEmpty().toMutableMap()
        val misses = cached?.misses.orEmpty().toMutableSet()
        var fresh = false

        // Exactos aún no cubiertos por la caché (ni rango ni negativos).
        val exactIds = visibles.mapNotNull { it.tvgId?.takeIf { t -> t.isNotBlank() } }.distinct()
        val missingExact = exactIds.filter { !range.containsKey(it) }
        if (missingExact.isNotEmpty()) {
            fresh = true
            nn.putAll(epgDao.nowNext(missingExact, now))
            range.putAll(timelineOf(missingExact, window))
            // Negativos: sin programas → lista vacía para no re-preguntar.
            for (id in missingExact) range.putIfAbsent(id, emptyList())
        }

        // Fallback aproximado SOLO en misses exactos no resueltos (ni por
        // la caché ni por intentos previos del mismo anchor).
        val matcher = EpgMatcher(epgDao)
        var matcherCalls = 0
        val approxFresh = mutableMapOf<String, String>()
        for (ch in visibles) {
            if (misses.contains(ch.stableKey)) continue
            if (BrowserEpgResolver.resolveKey(ch.tvgId, range, approx[ch.stableKey]) != null) continue
            matcherCalls++
            val hit = matcher.match(ch)
            if (hit == null) {
                misses += ch.stableKey
            } else {
                approxFresh[ch.stableKey] = hit.tvgId
            }
        }
        val approxIds = approxFresh.values.distinct().filter { !range.containsKey(it) }
        if (approxIds.isNotEmpty()) {
            fresh = true
            nn.putAll(epgDao.nowNext(approxIds, now))
            range.putAll(timelineOf(approxIds, window))
            for (id in approxIds) range.putIfAbsent(id, emptyList())
        }
        approx.putAll(approxFresh)

        EpgWindowCache.put(anchor, EpgWindowCache.Entry(nn, range, approx, misses))
        return Result(nn, range, approx, cacheHit = !fresh, matcherCalls = matcherCalls)
    }
}
