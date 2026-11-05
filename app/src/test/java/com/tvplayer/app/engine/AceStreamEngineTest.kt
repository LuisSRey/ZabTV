package com.tvplayer.app.engine

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
}
