package com.tvplayer.app.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S4: ExternalGate — matriz de veto del reproductor externo. */
class ExternalGateTest {

    private fun ok(
        url: String = "https://cdn.example.com/canal.m3u8",
        allowHttp: Boolean = false,
        engineProxy: String? = null,
        override: Boolean? = null,
        global: Boolean = true,
    ) = ExternalGate.canExport(url, allowHttp, engineProxy, override, global)

    @Test
    fun `https con global ON exporta`() {
        assertTrue(ok())
    }

    @Test
    fun `global OFF y sin override no exporta`() {
        assertFalse(ok(global = false))
    }

    @Test
    fun `override Siempre gana a global OFF`() {
        assertTrue(ok(override = true, global = false))
    }

    @Test
    fun `override Nunca gana a global ON`() {
        assertFalse(ok(override = false, global = true))
    }

    @Test
    fun `effectiveAllow resuelve override sobre global`() {
        assertTrue(ExternalGate.effectiveAllow(null, true))
        assertFalse(ExternalGate.effectiveAllow(null, false))
        assertTrue(ExternalGate.effectiveAllow(true, false))
        assertFalse(ExternalGate.effectiveAllow(false, true))
    }

    @Test
    fun `acestream se veta (va por startContentIntent, no VIEW generico)`() {
        assertFalse(ok(url = "acestream://deadbeef1234", global = true))
    }

    @Test
    fun `loopback-ace proxiﬁcado se veta`() {
        assertFalse(
            ok(
                url = "http://127.0.0.1:6878/ace/getstream?id=deadbeef",
                allowHttp = true,
                engineProxy = "127.0.0.1:6878",
                global = true,
            ),
        )
    }

    @Test
    fun `esquema no http se veta antes de tocar nada`() {
        assertFalse(ok(url = "rtmp://cdn.example.com/live", global = true))
        assertFalse(ok(url = "udp://@239.0.0.1:1234", global = true))
    }

    @Test
    fun `http con cleartext OFF se veta`() {
        assertFalse(ok(url = "http://cdn.example.com/canal.m3u8", allowHttp = false, global = true))
    }

    @Test
    fun `http con cleartext ON exporta`() {
        assertTrue(ok(url = "http://cdn.example.com/canal.m3u8", allowHttp = true, global = true))
    }
}
