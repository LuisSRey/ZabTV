package com.zabtv.app.core.xmltv

/**
 * Ventana de guía propia (S3): 3 h desde un anchor truncado a cortes de 30 min.
 * Puro JVM sin Android — la cabecera del timeline sale de [slots].
 */
data class GuideWindow(
    val anchorMillis: Long,
    val slotMinutes: Int = DEFAULT_SLOT_MINUTES,
    val slotCount: Int = DEFAULT_SLOT_COUNT,
) {
    val startMillis: Long = anchorMillis
    val endMillis: Long = anchorMillis + slotCount.toLong() * slotMinutes * 60_000L

    /** Inicios de cada corte de la cabecera (6 × 30 min por defecto). */
    fun slots(): List<Long> =
        (0 until slotCount).map { anchorMillis + it.toLong() * slotMinutes * 60_000L }

    companion object {
        const val DEFAULT_SLOT_MINUTES = 30
        const val DEFAULT_SLOT_COUNT = 6

        /** Trunca now al corte anterior (p. ej. 19:42 → 19:30). */
        fun anchorFor(nowMillis: Long, slotMinutes: Int = DEFAULT_SLOT_MINUTES): Long {
            val slot = slotMinutes * 60_000L
            return nowMillis - ((nowMillis % slot) + slot) % slot
        }

        fun now(nowMillis: Long = System.currentTimeMillis()): GuideWindow =
            GuideWindow(anchorFor(nowMillis))

        /** Progreso (now-start)/(end-start) clamped a [0,1]; 0 si la ventana es vacía. */
        fun progress(nowMillis: Long, startMillis: Long, endMillis: Long): Float {
            if (endMillis <= startMillis) return 0f
            return ((nowMillis - startMillis).toFloat() / (endMillis - startMillis)).coerceIn(0f, 1f)
        }
    }
}
