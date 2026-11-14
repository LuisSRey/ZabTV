package com.tvplayer.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SECCION-01 (T-P0-1 · D3/A3-03): clasificador puro de vetos — la misma lógica
 * de ExternalGate.canExport pero devolviendo la CAUSA, para que la UX mapee
 * cada causa a un mensaje accionable específico. canExport NO cambia: los
 * vetos son los mismos, solo se clasifican.
 *
 * Precedencia (primer match gana): ACESTREAM → ENGINE_LOOPBACK →
 * BAD_SCHEME/USER_INFO (descomposición de !isAllowedHttpUrl) → CLEARTEXT →
 * POLICY_OFF. Sin veto = null.
 */
class ExternalVetoTest {

    @Test
    fun `acestream se clasifica ACESTREAM`() {
        assertEquals(
            ExternalGate.Veto.ACESTREAM,
            ExternalGate.vetoOf("acestream://deadbeef1234", false, null, null, true),
        )
    }

    @Test
    fun `loopback proxificado se clasifica ENGINE_LOOPBACK`() {
        assertEquals(
            ExternalGate.Veto.ENGINE_LOOPBACK,
            ExternalGate.vetoOf("http://127.0.0.1:6878/ace/getstream?id=x", false, "127.0.0.1:6878", null, true),
        )
    }

    @Test
    fun `esquema no http se clasifica BAD_SCHEME`() {
        assertEquals(
            ExternalGate.Veto.BAD_SCHEME,
            ExternalGate.vetoOf("rtmp://srv/live", false, null, null, true),
        )
    }

    @Test
    fun `user info se clasifica USER_INFO`() {
        assertEquals(
            ExternalGate.Veto.USER_INFO,
            ExternalGate.vetoOf("http://user:pass@host/x", false, null, null, true),
        )
    }

    @Test
    fun `http con cleartext OFF se clasifica CLEARTEXT`() {
        assertEquals(
            ExternalGate.Veto.CLEARTEXT,
            ExternalGate.vetoOf("http://host/x.m3u8", false, null, null, true),
        )
    }

    @Test
    fun `https con global OFF y sin override se clasifica POLICY_OFF`() {
        assertEquals(
            ExternalGate.Veto.POLICY_OFF,
            ExternalGate.vetoOf("https://host/x.m3u8", false, null, null, false),
        )
    }

    @Test
    fun `https con global ON sin veto`() {
        assertNull(ExternalGate.vetoOf("https://host/x.m3u8", false, null, null, true))
    }

    @Test
    fun `override Siempre con global OFF no veta por politica`() {
        assertNull(ExternalGate.vetoOf("https://host/x.m3u8", false, null, true, false))
    }

    @Test
    fun `override Nunca con global ON veta POLICY_OFF`() {
        assertEquals(
            ExternalGate.Veto.POLICY_OFF,
            ExternalGate.vetoOf("https://host/x.m3u8", false, null, false, true),
        )
    }

    @Test
    fun `precedencia acestream gana aunque global ON y override Siempre`() {
        assertEquals(
            ExternalGate.Veto.ACESTREAM,
            ExternalGate.vetoOf("acestream://deadbeef1234", true, null, true, true),
        )
    }

    @Test
    fun `precedencia loopback gana aunque la URL sea localhost valida con allowHttp`() {
        assertEquals(
            ExternalGate.Veto.ENGINE_LOOPBACK,
            ExternalGate.vetoOf("http://127.0.0.1:6878/ace/getstream?id=x", true, "127.0.0.1:6878", null, true),
        )
    }

    @Test
    fun `precedencia POLICY_OFF solo si nada mas veta`() {
        // CLEARTEXT (http sin allowHttp) va antes que POLICY_OFF
        assertEquals(
            ExternalGate.Veto.CLEARTEXT,
            ExternalGate.vetoOf("http://host/x.m3u8", false, null, null, false),
        )
        // solo la política apagada produce POLICY_OFF
        assertEquals(
            ExternalGate.Veto.POLICY_OFF,
            ExternalGate.vetoOf("https://host/x.m3u8", false, null, null, false),
        )
    }

    @Test
    fun `canExport intacto las mismas entradas dan el mismo resultado`() {
        // espejo de ExternalGateTest con las mismas URLs: vetoOf == null <=> canExport == true
        val cases = listOf<Array<Any?>>(
            arrayOf("https://cdn.example.com/canal.m3u8", false, null, null, true),
            arrayOf("acestream://deadbeef1234", true, null, true, true),
            arrayOf("http://127.0.0.1:6878/ace/getstream?id=x", true, "127.0.0.1:6878", null, true),
            arrayOf("rtmp://cdn.example.com/live", false, null, null, true),
            arrayOf("http://user:pass@evil.com/s.m3u8", false, null, null, true),
            arrayOf("http://cdn.example.com/canal.m3u8", false, null, null, true),
            arrayOf("http://cdn.example.com/canal.m3u8", true, null, null, true),
        )
        for (c in cases) {
            val veto = ExternalGate.vetoOf(c[0] as String, c[1] as Boolean, c[2] as String?, c[3] as Boolean?, c[4] as Boolean)
            val can = ExternalGate.canExport(c[0] as String, c[1] as Boolean, c[2] as String?, c[3] as Boolean?, c[4] as Boolean)
            assertEquals("vetoOf/canExport divergen para ${c[0]}", can, veto == null)
        }
    }
}
