package com.tvplayer.app.data.repo

/**
 * Resultado de una importación (plan Task 6): números de auditoría + guards.
 * `rejected=true` significa NADA escrito en la tabla (regla del 10 %, cap de
 * 30 MB u otro aborto limpio).
 */
data class ImportResult(
    val listId: Long,
    val saved: Int,
    val skipped: Int,
    val errorLines: Int,
    val log: List<String>,
    val durationMs: Long,
    val rejected: Boolean,
    val message: String? = null,
)
