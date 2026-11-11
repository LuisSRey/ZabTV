package com.tvplayer.app.ui.channels

import com.tvplayer.app.data.db.EpgProgramEntity

/**
 * Clave EPG a pintar por canal en el browser (S3): exacta con programas
 * crudos en el rango → fallback aproximado ([com.tvplayer.app.data.repo.EpgMatcher])
 * → null sin guía. Nunca "" (los blancos se normalizan a null).
 *
 * Puro JVM: el caller aporta el rango ya consultado y el hit aproximado;
 * la consulta al matcher vive en el fragment (solo en misses exactos).
 */
object BrowserEpgResolver {

    fun resolveKey(
        tvgId: String?,
        range: Map<String, List<EpgProgramEntity>>,
        approxTvgId: String?,
    ): String? {
        val exact = tvgId?.takeIf { it.isNotBlank() }
        if (exact != null && !range[exact].isNullOrEmpty()) return exact
        val approx = approxTvgId?.takeIf { it.isNotBlank() && it != exact }
        if (approx != null && !range[approx].isNullOrEmpty()) return approx
        return null
    }
}
