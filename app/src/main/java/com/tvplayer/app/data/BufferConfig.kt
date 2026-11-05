package com.tvplayer.app.data

import com.tvplayer.app.data.db.AppDatabase

/**
 * Buffer del reproductor (spec §6): back-buffer 8 s / min 15 s / max 30 s.
 * Persistido en SettingsDao; sin valores → defaults del spec.
 */
data class BufferConfig(
    val minMs: Int = DEFAULT_MIN_MS,
    val maxMs: Int = DEFAULT_MAX_MS,
    val backMs: Int = DEFAULT_BACK_MS,
) {
    companion object {
        const val DEFAULT_MIN_MS = 15_000
        const val DEFAULT_MAX_MS = 30_000
        const val DEFAULT_BACK_MS = 8_000
        const val KEY_MIN = "buffer_min_ms"
        const val KEY_MAX = "buffer_max_ms"
        const val KEY_BACK = "buffer_back_ms"

        suspend fun load(db: AppDatabase): BufferConfig {
            val min = db.settingsDao().get(KEY_MIN, null)?.toIntOrNull() ?: DEFAULT_MIN_MS
            val max = db.settingsDao().get(KEY_MAX, null)?.toIntOrNull() ?: DEFAULT_MAX_MS
            val back = db.settingsDao().get(KEY_BACK, null)?.toIntOrNull() ?: DEFAULT_BACK_MS
            return BufferConfig(min, max, back).clamped()
        }

        suspend fun save(db: AppDatabase, config: BufferConfig) {
            val c = config.clamped()
            db.settingsDao().put(KEY_MIN, c.minMs.toString())
            db.settingsDao().put(KEY_MAX, c.maxMs.toString())
            db.settingsDao().put(KEY_BACK, c.backMs.toString())
        }
    }

    /** Sanea valores absurdos a algo reproducible y seguro. */
    fun clamped(): BufferConfig {
        val safeMax = maxMs.coerceAtLeast(1_000)
        val safeMin = minMs.coerceIn(1_000, safeMax)
        return copy(minMs = safeMin, maxMs = safeMax, backMs = backMs.coerceAtLeast(0))
    }
}
