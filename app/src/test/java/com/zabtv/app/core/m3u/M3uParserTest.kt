package com.zabtv.app.core.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class M3uParserTest {

    @Test
    fun `documento normal parsea header canal y url`() {
        val doc = M3uParser.parse(
            StringReader(
                """
                #EXTM3U x-tvg-url="http://epg.example.com/guide.xml"
                #EXTINF:-1 tvg-id="a.ext" tvg-name="A HD" tvg-logo="http://l/a.png" group-title="News",Canal A
                http://stream.example.com/a.m3u8
                """.trimIndent(),
            ),
        )
        assertEquals("http://epg.example.com/guide.xml", doc.header?.epgUrl)
        val ch = doc.channels.first()
        assertEquals("a.ext", ch.tvgId)
        assertEquals("Canal A", ch.name)
        assertEquals("http://stream.example.com/a.m3u8", ch.url)
        assertEquals("http://l/a.png", ch.logoUrl)
        assertEquals("News", ch.groupTitle)
    }

    @Test
    fun `sin EXTM3U header y comentarios sueltos`() {
        val doc = M3uParser.parse(
            StringReader(
                """
                /random/garbage/line
                #EXTINF:-1 tvg-id="b.ext",Canal B
                http://stream.example.com/b.ts
                """.trimIndent(),
            ),
        )
        assertNull(doc.header)
        assertEquals(1, doc.channels.toList().size)
    }

    @Test
    fun `BOM inicial y CRLF`() {
        val text = "\uFEFF#EXTM3U\r\n#EXTINF:-1 tvg-id=\"c.ext\",Canal C\r\nhttp://s/c.m3u8\r\n"
        val doc = M3uParser.parse(StringReader(text))
        assertEquals("Canal C", doc.channels.first().name)
    }

    @Test
    fun `atributos repetidos el ultimo gana`() {
        val doc = M3uParser.parse(
            StringReader(
                """
                #EXTM3U
                #EXTINF:-1 tvg-id="x.ext" tvg-id="y.ext",Doble
                http://s/d.m3u8
                """.trimIndent(),
            ),
        )
        assertEquals("y.ext", doc.channels.first().tvgId)
    }

    @Test
    fun `nombre con comas va hasta la ultima coma`() {
        val doc = M3uParser.parse(
            StringReader(
                """
                #EXTM3U
                #EXTINF:-1 tvg-id="n.ext",Ventas, S.A. de CV - 24 Horas
                http://s/n.m3u8
                """.trimIndent(),
            ),
        )
        assertEquals("Ventas, S.A. de CV - 24 Horas", doc.channels.first().name)
    }

    @Test
    fun `EXTVLCOPT y urls acestream preservadas tal cual`() {
        val doc = M3uParser.parse(
            StringReader(
                """
                #EXTM3U
                #EXTVLCOPT:--network-caching=1000
                #EXTINF:-1,Peer
                acestream://1a2b3c
                """.trimIndent(),
            ),
        )
        assertEquals("acestream://1a2b3c", doc.channels.first().url)
    }

    @Test
    fun `lineas basura y urls cortas no crashean`() {
        val doc = M3uParser.parse(
            StringReader(
                """
                #EXTM3U
                #EXTINF:-1,Completo
                http://s/ok.m3u8
                #EXTINF:-1,Solo
                https://
                123
                """.trimIndent(),
            ),
        )
        // "https://" se PRESERVA (parser = sintaxis); UrlPolicer la rechaza en Task 6 → skipped.
        // "123" (línea sin EXTINF previo) sí se descarta aquí.
        val channels = doc.channels.toList()
        assertEquals(2, channels.size)
        assertEquals("https://", channels[1].url)
    }

    @Test
    fun `10k canales en menos de 1 segundo JVM`() {
        val sb = StringBuilder("#EXTM3U\n")
        repeat(10_000) { i ->
            sb.append("#EXTINF:-1 tvg-id=\"c$i.ext\" tvg-logo=\"http://l/$i.png\" group-title=\"G${i % 20}\",Canal $i\n")
            sb.append("http://stream.example.com/$i.m3u8\n")
        }
        val t0 = System.nanoTime()
        val count = M3uParser.parse(StringReader(sb.toString())).channels.toList().size
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000
        assertEquals(10_000, count)
        assertTrue("parse $elapsedMs ms", elapsedMs < 1000)
    }
}
