package com.tvplayer.app.ui.channels

import com.tvplayer.app.data.db.EpgProgramEntity

/**
 * Caché de la ventana EPG vigente (S4): UNA sola entrada (cap 1) por
 * [anchorMillis] con la unión acumulada de lo ya consultado — tira
 * ahora/siguiente ([nn]), programas del rango ([range]), aproximados por
 * canal ([approx]) y canales ya vistos sin guía ([misses]).
 *
 * Validez: solo dentro del mismo anchor (≤30 min, la misma política de
 * staleness del repintado actual: la ventana solo se recomputa en
 * lista/filtro/reload/rotación). Un anchor nuevo desaloja la entrada.
 */
object EpgWindowCache {

    data class Entry(
        val nn: Map<String, Pair<EpgProgramEntity?, EpgProgramEntity?>> = emptyMap(),
        val range: Map<String, List<EpgProgramEntity>> = emptyMap(),
        /** stableKey → tvgId aproximado (hits del matcher). */
        val approx: Map<String, String> = emptyMap(),
        /** stableKeys ya matcheados sin guía (negativos: no re-preguntar). */
        val misses: Set<String> = emptySet(),
    )

    private var anchorMillis: Long? = null
    private var entry: Entry? = null

    @Synchronized
    fun get(anchorMillis: Long): Entry? =
        if (anchorMillis == this.anchorMillis) entry else null

    @Synchronized
    fun put(anchorMillis: Long, entry: Entry) {
        this.anchorMillis = anchorMillis
        this.entry = entry
    }

    @Synchronized
    fun invalidate() {
        anchorMillis = null
        entry = null
    }
}
