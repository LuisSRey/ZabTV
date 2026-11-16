package com.zabtv.app.data.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOW 2 (RED): la puerta del probe debe aplicar UrlPolicer + respetar
 * allowHttp para http, igual que el resto de la app, SIN hacer la petición
 * si no pasa.
 */
class ProxyProbeGateTest {

    @Test
    fun `https permitida con y sin allowHttp`() {
        assertTrue(ProxyProbeGate.isProbeAllowed("https://example.com/x", allowHttp = false))
        assertTrue(ProxyProbeGate.isProbeAllowed("https://example.com/x", allowHttp = true))
    }

    @Test
    fun `http bloqueada sin allowHttp y permitida con allowHttp`() {
        assertFalse(ProxyProbeGate.isProbeAllowed("http://example.com/x", allowHttp = false))
        assertTrue(ProxyProbeGate.isProbeAllowed("http://example.com/x", allowHttp = true))
    }

    @Test
    fun `rechaza no http(s), user-info y espacios`() {
        assertFalse(ProxyProbeGate.isProbeAllowed("notaurl", allowHttp = true))
        assertFalse(ProxyProbeGate.isProbeAllowed("", allowHttp = true))
        assertFalse(ProxyProbeGate.isProbeAllowed("file:///etc/passwd", allowHttp = true))
        assertFalse(ProxyProbeGate.isProbeAllowed("ftp://host/file", allowHttp = true))
        assertFalse(ProxyProbeGate.isProbeAllowed("javascript:alert(1)", allowHttp = true))
        assertFalse(ProxyProbeGate.isProbeAllowed("http://user:pass@evil.com/s.m3u8", allowHttp = true))
        assertFalse(ProxyProbeGate.isProbeAllowed("http://exa mple.com/a", allowHttp = true))
        // http con allowHttp=false se bloquea aunque la URL sea válida.
        assertFalse(ProxyProbeGate.isProbeAllowed("http://example.com/a", allowHttp = false))
    }
}
