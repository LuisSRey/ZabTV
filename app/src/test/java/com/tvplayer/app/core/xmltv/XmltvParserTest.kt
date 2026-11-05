package com.tvplayer.app.core.xmltv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XmltvParserTest {

    /** nowEpoch fijo: 2026-11-05T12:00:00Z (milisegundos). */
    private val now = 1_793_880_000_000L

    private fun xml(vararg programs: String): ByteArrayInputStream {
        val sb = StringBuilder()
        sb.append("<tv>\n<channel id=\"c1.ext\">\n<display-name>Canal Uno</display-name>\n</channel>\n")
        for (p in programs) sb.append(p).append('\n')
        sb.append("</tv>")
        return ByteArrayInputStream(sb.toString().toByteArray(StandardCharsets.UTF_8))
    }

    private fun programme(start: String, stop: String, title: String = "T'): Título &quot;X&quot;") =
        "<programme channel=\"c1.ext\" start=\"$start\" stop=\"$stop\"><title lang=\"es\">$title</title><desc>Desc</desc></programme>"

    @Test
    fun `canal y programas en ventana`() {
        val results = XmltvParser.parse(
            xml(programme("20261105120000 +0000", "20261105130000 +0000"), programme("20261105130000 +0000", "20261105140000 +0000")),
            now,
        )
        assertEquals(1, results.channels.size)
        assertEquals("Canal Uno", results.channels[0].displayName)
        assertEquals(2, results.programmes.toList().size)
    }

    @Test
    fun `fuera de ventana se descarta antes de materializar`() {
        val results = XmltvParser.parse(
            xml(
                programme("20261105120000 +0000", "20261105130000 +0000"),           // en ventana
                programme("20260101120000 +0000", "20260101130000 +0000"),           // pasado
                programme("20271231120000 +0000", "20271231130000 +0000"),           // futuro lejano
            ),
            now,
        )
        assertEquals(1, results.programmes.toList().size)
    }

    @Test
    fun `timezone offsets variados`() {
        val results = XmltvParser.parse(
            xml(programme("20261105130000 +0100", "20261105140000 +0100")),
            now,
        )
        assertEquals(1, results.programmes.toList().size)
    }

    @Test
    fun `entidades html decodificadas en titulo`() {
        val results = XmltvParser.parse(xml(programme("20261105120000 +0000", "20261105130000 +0000")), now)
        assertEquals("T'): Título \"X\"", results.programmes.first().title)
    }

    @Test
    fun `documento truncado no crashea y termina la secuencia`() {
        val broken = ByteArrayInputStream("<tv><channel id=\"c1.ext\"><display-name>Ca".toByteArray())
        val results = XmltvParser.parse(broken, now)
        assertTrue(results.programmes.toList().isEmpty())
    }

    @Test
    fun `50k programas en menos de 10 segundos JVM`() {
        val sb = StringBuilder()
        repeat(50_000) { i ->
            sb.append("<programme channel=\"c1.ext\" start=\"20261105${"%02d".format(i % 24)}0000 +0000\" stop=\"20261105${"%02d".format(i % 24)}3000 +0000\"><title>P$i</title></programme>")
        }
        val input = xml(sb.toString())
        val t0 = System.nanoTime()
        val list = XmltvParser.parse(input, now).programmes.toList()
        val elapsed = (System.nanoTime() - t0) / 1_000_000
        assertTrue("parse $elapsed ms", elapsed < 10_000)
        assertTrue(list.isNotEmpty())
    }
}
