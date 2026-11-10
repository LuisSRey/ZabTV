package com.tvplayer.app.data.net

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S2: DohConfig — presets + custom con validación y fallback a System. */
class DohConfigTest {

    @Test
    fun `default es System`() {
        assertEquals(DohConfig.System, DohConfig.from(null, null))
        assertEquals(DohConfig.System, DohConfig.from("system", null))
        assertEquals(DohConfig.System, DohConfig.from("desconocido", null))
    }

    @Test
    fun `presets resuelven`() {
        assertEquals(DohConfig.Cloudflare, DohConfig.from("cloudflare", null))
        assertEquals(DohConfig.Google, DohConfig.from("google", null))
    }

    @Test
    fun `custom valida acepta solo https con host`() {
        assertEquals(
            DohConfig.Custom("https://mi.dns/dns-query"),
            DohConfig.from("custom", "https://mi.dns/dns-query"),
        )
        assertFalse(DohConfig.isValidHttpsUrl("http://mi.dns/dns-query"))
        assertFalse(DohConfig.isValidHttpsUrl("https://"))
        assertFalse(DohConfig.isValidHttpsUrl("no-es-url"))
        assertFalse(DohConfig.isValidHttpsUrl(""))
    }

    @Test
    fun `custom invalida cae a System`() {
        assertEquals(DohConfig.System, DohConfig.from("custom", "http://inseguro/x"))
        assertEquals(DohConfig.System, DohConfig.from("custom", null))
        assertEquals(DohConfig.System, DohConfig.from("custom", "  "))
    }

    @Test
    fun `isValidHttpsUrl acepta url de preset google rfc8484`() {
        assertTrue(DohConfig.isValidHttpsUrl("https://dns.google/dns-query"))
    }

    @Test
    fun `uri trampa no cuela`() {
        assertFalse(DohConfig.isValidHttpsUrl("https:// user:pass@host/"))
    }
}
