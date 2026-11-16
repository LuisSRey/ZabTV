package com.zabtv.app.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.zabtv.app.core.Normalizer
import com.zabtv.app.core.StableKey
import com.zabtv.app.core.UrlPolicyException
import com.zabtv.app.core.UrlPolicer
import com.zabtv.app.core.m3u.M3uParser
import com.zabtv.app.data.db.AppDatabase
import com.zabtv.app.data.db.ChannelEntity
import com.zabtv.app.data.db.ListEntity
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Pipeline de importación (plan Task 6 / spec F1–F4, §6.3):
 * - Descarga/lectura en streaming, JAMÁS la lista completa en RAM; cap 30 MB
 *   (Content-Length + cap de bytes en el stream para chunked).
 * - URL de canal no permitida por UrlPolicer → skipped + log (no toca DB ni decoder).
 * - **Regla del 10 %** (guard de reimportación): si lo válido es < ceil(10 % de
 *   lo anterior) NO se toca la tabla: rejected=true.
 * - **Cancelación limpia**: checkpoints de ensureActive — o se escribe TODO
 *   (una transacción delete+insert al final) o NADA.
 */
class ImportService(
    private val appContext: Context,
    private val db: AppDatabase,
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES,
) {

    companion object {
        /** Límite del spec §6: M3U ≤ 30 MB. */
        const val DEFAULT_MAX_BODY_BYTES = 30_000_000L
        private const val MAX_LOG = 12
    }

    suspend fun import(listId: Long?, source: ImportSource): ImportResult {
        val t0 = System.currentTimeMillis()
        val targetListId = listId ?: createListRow()

        return try {
            val raw = withContext(ioDispatcher) { drain(targetListId, source) }
            commit(targetListId, raw, t0)
        } catch (abort: ImportAbortedException) {
            markList(targetListId, "error")
            ImportResult(
                listId = targetListId, saved = 0, skipped = 0, errorLines = 0,
                log = listOfNotNull(abort.message), durationMs = System.currentTimeMillis() - t0,
                rejected = true, message = abort.message,
            )
        } catch (bad: UrlPolicyException) {
            markList(targetListId, "error")
            ImportResult(
                listId = targetListId, saved = 0, skipped = 0, errorLines = 0,
                log = listOfNotNull(bad.message), durationMs = System.currentTimeMillis() - t0,
                rejected = true, message = bad.message,
            )
        } catch (cancell: CancellationException) {
            // Cancelación: la transacción nunca empezó — tabla intacta por construcción
            markList(targetListId, "error")
            ImportResult(
                listId = targetListId, saved = 0, skipped = 0, errorLines = 0,
                log = listOf("import cancelado"), durationMs = System.currentTimeMillis() - t0,
                rejected = false, message = "importación cancelada",
            )
        }
    }

    // ---------- fases ----------

    private class Raw(
        val saved: Int,
        val skipped: Int,
        val errorLines: Int,
        val log: List<String>,
        val epgHeaderUrl: String?,
        val channels: List<ChannelEntity>,
    )

    private suspend fun drain(listId: Long, source: ImportSource): Raw {
        coroutineContext.ensureActive()
        return when (source) {
            is ImportSource.HttpSource -> {
                UrlPolicer.requireAllowedHttpUrl(source.url)
                val builder = Request.Builder().url(source.url)
                source.userAgent?.let { builder.header("User-Agent", it) }
                client.newCall(builder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        resp.body?.close()
                        throw ImportAbortedException("servidor respondió HTTP ${resp.code}")
                    }
                    val body = resp.body ?: throw ImportAbortedException("respuesta sin cuerpo")
                    val declared = body.contentLength()
                    if (declared > maxBodyBytes) {
                        throw ImportAbortedException("lista mayor que el límite de 30 MB ($declared bytes declarados)")
                    }
                    return parseFrom(listId, InputStreamReader(CountingInputStream(body.byteStream()), Charsets.UTF_8))
                }
            }
            is ImportSource.FileSource -> {
                val input = appContext.contentResolver.openInputStream(source.uri)
                    ?: throw ImportAbortedException("No se pudo abrir el archivo")
                return parseFrom(listId, InputStreamReader(CountingInputStream(input), Charsets.UTF_8))
            }
        }
    }

    private suspend fun parseFrom(listId: Long, reader: Reader): Raw {
        coroutineContext.ensureActive()
        val doc = M3uParser.parse(reader)
        coroutineContext.ensureActive()

        val log = mutableListOf<String>()
        var skipped = 0
        var saved = 0
        val channels = ArrayList<ChannelEntity>(1024)
        val usedKeys = HashSet<String>(1024)
        for ((index, ch) in doc.channels.withIndex()) {
            coroutineContext.ensureActive()
            // Se conserva http(s) y acestream:// (se resuelve al reproducir vía
            // proxy local); el resto se descarta ANTES de DB/decoder (spec F8).
            val isAce = ch.url.startsWith("acestream://", ignoreCase = true)
            if (!isAce && !UrlPolicer.isAllowedHttpUrl(ch.url)) {
                skipped++
                if (log.size < MAX_LOG) log.add("skip línea ${index + 1}: URL no permitida (${ch.url.take(50)})")
                continue
            }
            val name = ch.name.ifBlank { ch.tvgName ?: "Canal" }
            val group = ch.groupTitle ?: ""
            // Clave única por importación: el mismo nombre/grupo (o tvg-id) en
            // varias filas NO se fusiona — cada servidor entra (spec: no borrar).
            // El sufijo es determinista (orden del documento) → reimports idénticos
            // generan las mismas claves y los favoritos sobreviven.
            val base = StableKey.of(ch.tvgId, name, group)
            var stableKey = base
            var n = 2
            while (!usedKeys.add(stableKey)) {
                stableKey = "$base#$n"
                n++
            }
            channels += ChannelEntity(
                listId = listId,
                tvgId = ch.tvgId,
                displayName = name,
                normName = Normalizer.normName(name),
                groupName = group,
                logoUrl = ch.logoUrl?.takeIf { UrlPolicer.isAllowedHttpUrl(it) },
                url = ch.url,
                stableKey = stableKey,
                position = saved,
            )
            saved++
        }
        return Raw(saved, skipped, errorLines = 0, log, epgHeaderUrl = doc.header?.epgUrl, channels)
    }

    private suspend fun commit(targetListId: Long, raw: Raw, t0: Long): ImportResult {
        // Guard de reimportación (spec §6.3): proporción insuficiente → NADA escrito
        coroutineContext.ensureActive()
        val prevCount = db.channelDao().countForList(targetListId)
        val threshold = if (prevCount > 0) Math.ceil(prevCount * 0.10).toInt() else 0
        if (threshold > 0 && raw.saved < threshold) {
            markList(targetListId, "error")
            val msg = "import rejected: below 10% rule (prev=$prevCount, threshold=$threshold, saved=${raw.saved})"
            return ImportResult(
                listId = targetListId, saved = prevCount, skipped = raw.skipped, errorLines = raw.errorLines,
                log = raw.log.take(MAX_LOG) + msg, durationMs = System.currentTimeMillis() - t0,
                rejected = true, message = msg,
            )
        }

        // ÚNICO punto de escritura: delete+insert completo de la lista en una transacción
        coroutineContext.ensureActive()
        db.withTransaction {
            db.channelDao().deleteForList(targetListId)
            if (raw.channels.isNotEmpty()) db.channelDao().upsertAll(raw.channels)
            val list = db.listDao().get(targetListId)
            if (list != null) {
                db.listDao().upsert(
                    list.copy(
                        epgUrl = raw.epgHeaderUrl ?: list.epgUrl,
                        lastImportedAt = t0,
                        status = "ok",
                    ),
                )
            }
        }
        return ImportResult(
            listId = targetListId, saved = raw.saved, skipped = raw.skipped, errorLines = raw.errorLines,
            log = raw.log.take(MAX_LOG), durationMs = System.currentTimeMillis() - t0,
            rejected = false, message = "import ok (${raw.saved} canales)",
        )
    }

    private suspend fun markList(listId: Long, status: String) {
        runCatching {
            val list = db.listDao().get(listId) ?: return
            db.listDao().upsert(list.copy(status = status))
        }
    }

    private suspend fun createListRow(): Long =
        db.listDao().upsert(ListEntity(name = "Nueva lista", sourceUrl = "", status = "importing"))

    /** Guard de bytes del stream: excede el cap → ImportAbortedException limpia. */
    private inner class CountingInputStream(src: InputStream) : FilterInputStream(src) {
        var count: Long = 0
            private set

        override fun read(): Int {
            val n = super.read()
            if (n >= 0) count++
            checkCap()
            return n
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) count += n
            checkCap()
            return n
        }

        private fun checkCap() {
            if (count > maxBodyBytes) {
                throw ImportAbortedException("lista mayor que el límite de 30 MB ($count bytes leídos)")
            }
        }
    }
}
