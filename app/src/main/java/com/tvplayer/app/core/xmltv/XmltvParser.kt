package com.tvplayer.app.core.xmltv

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.xmlpull.v1.XmlPullParserException

/**
 * Parser XMLTV por streaming (spec §3): UN SOLO XmlPullParser en dos fases —
 * fase 1 (eager) lee channels y se detiene ANTES de consumir el resto del primer
 * `programme`; sus atributos viajan como handoff a la secuencia perezosa (fase 2)
 * que continúa con el MISMO parser: sin rewind, sin residuo, sin doble lectura.
 *
 * Los programas fuera de ventana [now−12h, now+60h] se descartan ANTES de
 * materializar (spec F8, límite 120 MB aguas arriba en EpgService). Documento
 * truncado termina todo limpio sin crash.
 *
 * Ruling (ledger): `<channel>` solo se captura antes del primer `programme`
 * (orden estándar XMLTV).
 */
object XmltvParser {

    private class Handoff(val channel: String?, val start: Long?, val stop: Long?)

    fun parse(input: InputStream, nowMillis: Long): XmltvResult {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)

        val channels = mutableListOf<XmltvChannel>()
        var handoff: Handoff? = null
        var ended = false

        // ---- Fase 1: canales, hasta el primer <programme> o EOF ----
        runCatching {
            var id: String? = null
            var displayName: String? = null
            var inChannel = false
            var inDisplayName = false
            val sb = StringBuilder()
            loop@ while (true) {
                when (parser.next()) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "channel" -> {
                            inChannel = true
                            id = parser.getAttributeValue(null, "id")
                            displayName = null
                        }
                        "display-name" -> if (inChannel) {
                            inDisplayName = true
                            sb.setLength(0)
                        }
                        "programme" -> {
                            handoff = Handoff(
                                parser.getAttributeValue(null, "channel"),
                                parseDate(parser.getAttributeValue(null, "start")),
                                parseDate(parser.getAttributeValue(null, "stop")),
                            )
                            break@loop
                        }
                    }
                    XmlPullParser.TEXT -> if (inDisplayName) sb.append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "display-name" -> if (inDisplayName) {
                            if (displayName == null) displayName = sb.toString().trim().ifBlank { null }
                            inDisplayName = false
                        }
                        "channel" -> {
                            if (id != null) channels.add(XmltvChannel(id!!, displayName))
                            inChannel = false
                            id = null
                        }
                    }
                    XmlPullParser.END_DOCUMENT -> {
                        ended = true
                        break@loop
                    }
                }
            }
        }.onFailure {
            // truncado / IO: mantener lo leído, secuencia terminará limpia
            ended = true
        }

        // ---- Fase 2: programas perezosos sobre el MISMO parser ----
        val programmes = sequence {
            if (!ended) {
                var inProgramme = handoff != null
                var ch = handoff?.channel
                var start = handoff?.start ?: -1L
                var stop = handoff?.stop ?: -1L
                if (handoff != null) handoff = null
                var title: String? = null
                var desc: String? = null
                val sb = StringBuilder()
                var inTitle = false
                var inDesc = false
                try {
                    while (true) {
                        when (parser.next()) {
                            XmlPullParser.START_TAG -> when (parser.name) {
                                "programme" -> {
                                    inProgramme = true
                                    ch = parser.getAttributeValue(null, "channel")
                                    start = parseDate(parser.getAttributeValue(null, "start")) ?: -1L
                                    stop = parseDate(parser.getAttributeValue(null, "stop")) ?: -1L
                                    title = null; desc = null; inTitle = false; inDesc = false
                                }
                                "title" -> if (inProgramme) {
                                    inTitle = true
                                    sb.setLength(0)
                                }
                                "desc" -> if (inProgramme) {
                                    inDesc = true
                                    sb.setLength(0)
                                }
                            }
                            XmlPullParser.TEXT -> {
                                if (inTitle || inDesc) sb.append(parser.text)
                            }
                            XmlPullParser.END_TAG -> when (parser.name) {
                                "title" -> if (inTitle) {
                                    if (title == null) title = sb.toString().trim()
                                    inTitle = false
                                }
                                "desc" -> if (inDesc) {
                                    if (desc == null) desc = sb.toString().trim()
                                    inDesc = false
                                }
                                "programme" -> {
                                    if (ch != null && start >= 0 && stop > 0 && EpgWindow.inWindow(start, nowMillis)) {
                                        yield(
                                            XmltvProgramme(
                                                tvgId = ch!!,
                                                startMillis = start,
                                                endMillis = stop,
                                                title = title ?: "",
                                                description = desc?.takeIf { it.isNotBlank() },
                                            ),
                                        )
                                    }
                                    inProgramme = false
                                    ch = null
                                }
                            }
                            XmlPullParser.END_DOCUMENT -> break
                        }
                    }
                } catch (_: XmlPullParserException) {
                    // truncado: terminar la secuencia limpio (spec)
                } catch (_: IOException) {
                    // truncado
                }
            }
        }.constrainOnce()

        return XmltvResult(channels, programmes)
    }

    /** "yyyyMMddHHmmss +0000" | "yyyyMMddHHmmss +0100" | "+00:00" | sin offset (asume UTC). */
    internal fun parseDate(raw: String?): Long? {
        if (raw == null) return null
        val value = raw.trim()
        val base = value.substringBefore(' ').trim()
        val ldt = try {
            LocalDateTime.parse(base, FORMAT)
        } catch (_: Exception) {
            return null
        }
        val offsetPart = value.substringAfter(' ', "").trim()
        val offset = when {
            offsetPart.isEmpty() -> ZoneOffset.UTC
            else -> try {
                ZoneOffset.of(normaliseOffset(offsetPart))
            } catch (_: Exception) {
                ZoneOffset.UTC
            }
        }
        return ldt.toInstant(offset).toEpochMilli()
    }

    /** "+0530" → "+05:30" (ISO exige los dos puntos); "+05:30" ya válido → tal cual. */
    private fun normaliseOffset(part: String) = if (part.length == 5 && part[0] in "+-") {
        "${part.substring(0, 3)}:${part.substring(3)}"
    } else {
        part
    }

    private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
}
