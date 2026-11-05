package com.tvplayer.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

/**
 * El reproductor detecta si el motor AceStream local responde antes de
 * reproducir, para lanzar la app o avisar en vez de pantalla negra.
 */
class AceStreamEngineTest {

    @Test
    fun `puerto abierto responde`() {
        ServerSocket(0).use { server ->
            assertTrue(AceStreamEngine.isReachable("127.0.0.1", server.localPort))
        }
    }

    @Test
    fun `puerto cerrado no responde`() {
        // Puerto casi seguro cerrado (sin nada escuchando).
        assertFalse(AceStreamEngine.isReachable("127.0.0.1", 9, timeoutMs = 500))
    }

    @Test
    fun `splitHostPort entiende variantes`() {
        assertEquals("127.0.0.1" to 6878, AceStreamEngine.splitHostPort("127.0.0.1:6878"))
        assertEquals("host" to 1234, AceStreamEngine.splitHostPort("http://host:1234/"))
        assertEquals("example.com" to 6878, AceStreamEngine.splitHostPort("example.com"))
        assertEquals("127.0.0.1" to 6878, AceStreamEngine.splitHostPort(""))
        assertEquals("host" to 6878, AceStreamEngine.splitHostPort("host:abc"))
    }
}
