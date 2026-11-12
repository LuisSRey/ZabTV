package com.tvplayer.app.core.xmltv

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * S3: la ventana es epoch puro — independiente de la zona. Offsets XMLTV
 * distintos del mismo instante dan el mismo epoch y el anchor trunca igual
 * en cualquier zona por defecto. JVM puro.
 */
class GuideWindowTzTest {

    private val defaultTz = TimeZone.getDefault()

    @Before
    fun saveTz() {
        // Se lee en @After para restaurar lo que hubiera.
    }

    @After
    fun restoreTz() {
        TimeZone.setDefault(defaultTz)
    }

    @Test
    fun `offsets distintos del mismo instante dan el mismo epoch`() {
        val utc = XmltvParser.parseDate("20261105120000 +0000")
        val plusOne = XmltvParser.parseDate("20261105130000 +0100")
        val colon = XmltvParser.parseDate("20261105120000 +00:00")
        val bare = XmltvParser.parseDate("20261105120000")
        assertTrue(utc != null && plusOne != null && colon != null && bare != null)
        assertEquals(utc, plusOne)
        assertEquals(utc, colon)
        // Sin offset se asume UTC.
        assertEquals(utc, bare)
    }

    @Test
    fun `anchor trunca igual para ambas escrituras del instante`() {
        val a = XmltvParser.parseDate("20261105134200 +0000")!!
        val b = XmltvParser.parseDate("20261105144200 +0100")!!
        assertEquals(a, b)
        assertEquals(a - 12L * 60_000L, GuideWindow.anchorFor(a))
        assertEquals(GuideWindow.anchorFor(a), GuideWindow.anchorFor(b))
        assertEquals(0L, GuideWindow.anchorFor(a) % (30L * 60_000L))
    }

    @Test
    fun `anchor independiente de la zona por defecto`() {
        // 2026-11-10T19:42:00Z → 19:30:00Z en cualquier zona.
        val t = 1_794_296_520_000L
        val expected = t - 12L * 60_000L
        for (id in listOf("UTC", "Pacific/Kiritimati", "America/New_York")) {
            TimeZone.setDefault(TimeZone.getTimeZone(id))
            assertEquals("zona $id", expected, GuideWindow.anchorFor(t))
        }
    }
}
