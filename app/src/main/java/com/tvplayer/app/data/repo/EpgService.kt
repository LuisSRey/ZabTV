package com.tvplayer.app.data.repo

import android.content.Context
import com.tvplayer.app.core.UrlPolicer
import com.tvplayer.app.core.xmltv.XmltvParser
import com.tvplayer.app.data.db.AppDatabase
import com.tvplayer.app.data.db.EpgProgramEntity
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Ingesta EPG (Task 13 / spec §6): descarga por streaming con cap de 120 MB,
 * parseo perezoso con filtro de ventana [now−12h, now+60h] (XmltvParser) e
 * insertBatch cada 2 000 filas; al final poda lo anterior a 5 días.
 * Nunca la guía entera en RAM.
 */
class EpgService(
    private val appContext: Context,
    private val db: AppDatabase,
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {

    companion object {
        /** Límite del spec §6: XMLTV ≤ 120 MB. */
        const val DEFAULT_MAX_BODY_BYTES = 120_000_000L
        private const val BATCH = 2_000
        private const val FIVE_DAYS_MILLIS = 5L * 24 * 3_600_000
    }

    suspend fun refresh(url: String): EpgResult {
        val now = nowProvider()
        return try {
            withContext(ioDispatcher) { drain(url, now) }
        } catch (abort: ImportAbortedException) {
            EpgResult(inserted = 0, skippedOutOfWindow = 0, pruned = 0, rejected = true, message = abort.message)
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    private suspend fun drain(url: String, now: Long): EpgResult {
        coroutineContext.ensureActive()
        UrlPolicer.requireAllowedHttpUrl(url)
        val request = Request.Builder().url(url).build()
        var inserted = 0
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                resp.body?.close()
                throw ImportAbortedException("servidor respondió HTTP ${resp.code}")
            }
            val body = resp.body ?: throw ImportAbortedException("respuesta sin cuerpo")
            if (body.contentLength() > maxBodyBytes) {
                throw ImportAbortedException("guía mayor que el límite de 120 MB (${body.contentLength()} bytes declarados)")
            }
            val reader = CountingInputStream(body.byteStream())
            val doc = XmltvParser.parse(reader, now)
            coroutineContext.ensureActive()
            // Catálogo XMLTV para el fallback por nombre (Task 14)
            val epgChannels = doc.channels.map { com.tvplayer.app.data.db.EpgChannelEntity(it.id, it.displayName) }
            if (epgChannels.isNotEmpty()) db.epgDao().insertChannels(epgChannels)
            val batch = ArrayList<EpgProgramEntity>(BATCH)
            for (p in doc.programmes) {
                coroutineContext.ensureActive()
                batch += EpgProgramEntity(
                    tvgId = p.tvgId,
                    startMillis = p.startMillis,
                    endMillis = p.endMillis,
                    title = p.title,
                    description = p.description,
                )
                if (batch.size >= BATCH) {
                    db.epgDao().insertBatch(batch.toList())
                    inserted += batch.size
                    batch.clear()
                }
            }
            // Fuera de ventana: XmltvParser los descarta ANTES de materializar (spec F8),
            // así que el servicio solo ve los que entran: skipped no es observable aquí.
            if (batch.isNotEmpty()) {
                db.epgDao().insertBatch(batch.toList())
                inserted += batch.size
            }
        }
        val pruned = db.epgDao().pruneBefore(now - FIVE_DAYS_MILLIS)
        return EpgResult(inserted, skippedOutOfWindow = 0, pruned, rejected = false, message = "epg ok ($inserted programas)")
    }

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
                throw ImportAbortedException("guía mayor que el límite de 120 MB ($count bytes leídos)")
            }
        }
    }
}

/** Resultado de una ingesta EPG (Task 13). */
data class EpgResult(
    val inserted: Int,
    val skippedOutOfWindow: Int,
    val pruned: Int,
    val rejected: Boolean,
    val message: String? = null,
)
