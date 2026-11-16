package com.zabtv.app.data.repo

/**
 * Origen de importación (spec §5): HTTP (con UA configurable) o archivo local.
 * El lector lo abre ImportService (el HTTP por el cliente compartido — único
 * punto del CleartextGate Task 11; el archivo por ContentResolver).
 */
sealed interface ImportSource {
    data class HttpSource(val url: String, val userAgent: String?) : ImportSource
    data class FileSource(val uri: android.net.Uri) : ImportSource
}

/** Aborto de importación con mensaje humano (cap, guard 10 %, cancelación, red). */
class ImportAbortedException(message: String) : RuntimeException(message)
