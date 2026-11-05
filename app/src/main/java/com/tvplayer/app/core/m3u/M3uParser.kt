package com.tvplayer.app.core.m3u

import java.io.BufferedReader
import java.io.Reader

/**
 * Parser M3U por streaming (spec §3): la primera línea (header) se inspecciona
 * eager y el RESTO viaja como Sequence de pull — nunca la lista entera en RAM.
 * Casos corruptos (BOM, sin header, attrs repetidos, nombre con comas, líneas
 * basura) no paran el parse (spec F1). Sin dependencias.
 */
object M3uParser {

    private const val BOM = '\uFEFF'
    private val ATTR_RE = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
    private val HEADER_URL_RE = Regex("""(?:x-tvg-url|url-tvg)="([^"]*)"""", RegexOption.IGNORE_CASE)

    fun parse(reader: Reader): M3uDocument {
        val buffered = reader as? BufferedReader ?: BufferedReader(reader)

        var header: M3uHeader? = null
        val firstRaw = buffered.readLine()?.trim().orEmpty()
        var cleanFirst = if (firstRaw.startsWith(BOM)) firstRaw.substring(1).trim() else firstRaw
        var pushback: String? = null
        if (cleanFirst.startsWith("#EXTM3U")) {
            val epg = HEADER_URL_RE.find(cleanFirst)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            header = M3uHeader(epg)
            cleanFirst = ""
        } else if (cleanFirst.isNotEmpty()) {
            pushback = cleanFirst
        }

        val channels = sequence {
            var pending: M3uChannel? = null
            val rest = buffered.lineSequence()
            val lines: Sequence<String> = if (pushback != null) sequenceOf(pushback) + rest else rest
            for (raw in lines) {
                var line = raw.trim()
                if (line.isEmpty()) continue
                if (line[0] == BOM) line = line.substring(1).trim()
                if (line.isEmpty()) continue
                when {
                    line.startsWith("#EXTINF") -> pending = parseExtinf(line)
                    line.startsWith("#") -> Unit // #EXTVLCOPT y demás comentarios: ignorados
                    else -> {
                        val ch = pending
                        if (ch != null) {
                            pending = null
                            yield(M3uChannel(ch.tvgId, ch.tvgName, ch.logoUrl, ch.groupTitle, ch.name, line, ch.rawAttrs))
                        }
                        // URL sin EXTINF previo: descartada sin crash
                    }
                }
            }
        }.constrainOnce()

        return M3uDocument(header, channels)
    }

    private fun parseExtinf(line: String): M3uChannel? {
        val attrs = mutableMapOf<String, String>()
        // La coma separadora es la primera coma FUERA de comillas: los valores entrecomillados
        // y las comas del propio nombre sobreviven (hasta la última coma del valor final).
        var inQuote = false
        var separatorIdx = -1
        val chars = line.toCharArray()
        scan@ for (i in chars.indices) {
            when {
                chars[i] == '"' -> inQuote = !inQuote
                chars[i] == ',' && !inQuote -> {
                    separatorIdx = i
                    break@scan
                }
            }
        }
        val meta = line.substring(0, if (separatorIdx >= 0) separatorIdx else chars.size)
        val name = if (separatorIdx >= 0) line.substring(separatorIdx + 1).trim() else ""
        ATTR_RE.findAll(meta).forEach { m -> attrs[m.groupValues[1]] = m.groupValues[2] }
        if (name.isEmpty() && attrs.isEmpty()) return null
        return M3uChannel(
            tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
            tvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() },
            logoUrl = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
            groupTitle = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: attrs["group"]?.takeIf { it.isNotBlank() },
            name = name,
            url = "",
            rawAttrs = attrs.toMap(),
        )
    }
}
