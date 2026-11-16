package com.zabtv.app.data.net

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1: AppProxySelector — OFF → DIRECT siempre; ON → DIRECT solo loopback,
 * resto → proxy configurado (HTTP o SOCKS).
 */
class ProxySelectorTest {

    private fun on(host: String = "proxy.local", port: Int = 8080, type: ProxyType = ProxyType.HTTP) =
        AppProxySelector(ProxyConfig(enabled = true, host = host, port = port, type = type))

    @Test
    fun `OFF devuelve DIRECT aunque haya host configurado`() {
        val sel = AppProxySelector(ProxyConfig(enabled = false, host = "proxy.local", port = 8080))
        val proxies = sel.select(URI("https://example.com/lista.m3u"))
        assertEquals(listOf(Proxy.NO_PROXY), proxies)
    }

    @Test
    fun `ON dirige remoto al proxy HTTP configurado`() {
        val proxies = on().select(URI("https://example.com/lista.m3u"))
        assertEquals(1, proxies.size)
        val p = proxies[0]
        assertEquals(Proxy.Type.HTTP, p.type())
        val addr = p.address() as InetSocketAddress
        assertEquals("proxy.local", addr.hostString)
        assertEquals(8080, addr.port)
    }

    @Test
    fun `ON con SOCKS usa tipo SOCKS`() {
        val proxies = on(type = ProxyType.SOCKS).select(URI("https://example.com/x"))
        assertEquals(Proxy.Type.SOCKS, proxies[0].type())
    }

    @Test
    fun `ON deja loopback en DIRECT (127, localhost, ipv6)`() {
        val sel = on()
        listOf(
            "http://127.0.0.1:6878/ace/getstream?id=x",
            "http://localhost:6878/ace/getstream?id=x",
            "http://[::1]:6878/ace/getstream?id=x",
            "http://[0:0:0:0:0:0:0:1]:6878/x",
        ).forEach { url ->
            assertEquals("loopback debe ir DIRECT: $url", listOf(Proxy.NO_PROXY), sel.select(URI(url)))
        }
    }

    @Test
    fun `ON sin host valido cae a DIRECT`() {
        val sel = AppProxySelector(ProxyConfig(enabled = true, host = "  ", port = 8080))
        assertEquals(listOf(Proxy.NO_PROXY), sel.select(URI("https://example.com/x")))
    }

    @Test
    fun `connectFailed no lanza`() {
        on().connectFailed(URI("https://example.com/"), null, java.io.IOException("boom"))
        assertTrue(true)
    }
}
