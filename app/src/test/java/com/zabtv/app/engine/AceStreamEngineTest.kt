package com.zabtv.app.engine

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

    @Test
    fun `contentIdOf extrae el hash sin esquema ni espacios`() {
        assertEquals("abc123", AceStreamEngine.contentIdOf("acestream://abc123"))
        assertEquals("abc123", AceStreamEngine.contentIdOf("  ACESTREAM://abc123  "))
        assertEquals("", AceStreamEngine.contentIdOf("acestream://  "))
    }

    @Test
    fun `startContentDataUri usa el formato de la integracion basica`() {
        assertEquals(
            "acestream:?content_id=abc123",
            AceStreamEngine.startContentDataUri("abc123"),
        )
    }

    @Test
    fun `engineProxyFor detecta esquema acestream`() {
        assertEquals(
            "127.0.0.1:6878",
            AceStreamEngine.engineProxyFor("acestream://abc123", "127.0.0.1:6878"),
        )
        assertEquals(
            "127.0.0.1:6878",
            AceStreamEngine.engineProxyFor("  ACESTREAM://abc123  ", "127.0.0.1:6878"),
        )
    }

    @Test
    fun `engineProxyFor detecta url pre-proxificada en loopback`() {
        assertEquals(
            "127.0.0.1:6878",
            AceStreamEngine.engineProxyFor(
                "http://127.0.0.1:6878/ace/getstream?id=abc123",
                "127.0.0.1:6878",
            ),
        )
        assertEquals(
            "localhost:6878",
            AceStreamEngine.engineProxyFor(
                "http://localhost:6878/ace/getstream?id=abc123",
                "127.0.0.1:6878",
            ),
        )
    }

    @Test
    fun `engineProxyFor ignora lo que no necesita motor local`() {
        assertEquals(null, AceStreamEngine.engineProxyFor("https://example.com/live/1.m3u8", "127.0.0.1:6878"))
        // Remoto aunque parezca proxy ace: no es el motor local.
        assertEquals(null, AceStreamEngine.engineProxyFor("http://example.com:8080/ace/getstream?id=x", "127.0.0.1:6878"))
        // Loopback sin pinta de proxy ace: otro servidor local, sin dialogo.
        assertEquals(null, AceStreamEngine.engineProxyFor("http://127.0.0.1:8080/video.mp4", "127.0.0.1:6878"))
        assertEquals(null, AceStreamEngine.engineProxyFor("no-es-url", "127.0.0.1:6878"))
    }

    @Test
    fun `aceStreamUrlForView reconstruye acestream desde pre-proxificada`() {
        assertEquals("acestream://abc", AceStreamEngine.aceStreamUrlForView("acestream://abc"))
        assertEquals(
            "acestream://abc123",
            AceStreamEngine.aceStreamUrlForView("http://127.0.0.1:6878/ace/getstream?id=abc123"),
        )
        val raw = "http://127.0.0.1:6878/ace/getstream"
        assertEquals(raw, AceStreamEngine.aceStreamUrlForView(raw))
    }
}

class AceStreamStarterTest {

    @Test
    fun `elige el candidato con mayor version`() {
        val versions = mapOf("org.free.aceserve" to 10L, "org.acestream.node.web" to 42L)
        assertEquals(
            "org.acestream.node.web",
            AceStreamStarter.selectBest(versions.keys.toList()) { versions[it] ?: -1 },
        )
    }

    @Test
    fun `sin candidatos instalados devuelve null`() {
        assertEquals(null, AceStreamStarter.selectBest(listOf("org.free.aceserve")) { -1 })
        assertEquals(null, AceStreamStarter.selectBest(emptyList()) { 0 })
    }

    @Test
    fun `aceserve primero aunque tenga menor version`() {
        val versions = mapOf("org.acestream.node.web" to 42L, "org.free.aceserve" to 10L)
        assertEquals(
            listOf("org.free.aceserve", "org.acestream.node.web"),
            AceStreamStarter.orderCandidates(versions),
        )
    }

    @Test
    fun `sin aceserve ordena por version y excluye no instalados`() {
        val versions = mapOf(
            "org.acestream.node.web" to 42L,
            "org.acestream.media" to 50L,
            "org.acestream.core" to -1L,
        )
        assertEquals(
            listOf("org.acestream.media", "org.acestream.node.web"),
            AceStreamStarter.orderCandidates(versions),
        )
    }
}
