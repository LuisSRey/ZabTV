package com.tvplayer.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La excepción estática de cleartext es SOLO loopback (proxy AceStream local):
 * base denegada y ningún dominio de internet permitido. El resto del policy
 * lo gobierna CleartextGate en runtime.
 */
class CleartextConfigTest {

    private fun configFile(): File {
        val f = File("src/main/res/xml/network_security_config.xml")
        assertTrue("falta res/xml/network_security_config.xml", f.exists())
        return f
    }

    @Test
    fun `base deniega cleartext`() {
        val xml = configFile().readText()
        assertTrue(xml.contains("<base-config cleartextTrafficPermitted=\"false\""))
    }

    @Test
    fun `solo loopback permitido en claro`() {
        val xml = configFile().readText()
        val trueBlocks = Regex("<domain-config cleartextTrafficPermitted=\"true\">(.*?)</domain-config>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).map { it.groupValues[1] }.toList()
        assertEquals(1, trueBlocks.size)
        val domains = Regex("<domain[^>]*>([^<]+)</domain>").findAll(trueBlocks.first())
            .map { it.groupValues[1].trim().lowercase() }.toSet()
        assertEquals(setOf("127.0.0.1", "localhost"), domains)
        assertTrue(!xml.contains("usesCleartextTraffic"))
    }
}
