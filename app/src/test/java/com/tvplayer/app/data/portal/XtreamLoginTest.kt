package com.tvplayer.app.data.portal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 12: XtreamLogin es lógica pura (sin Android) — host variants, urlencoding
 * de credenciales y rechazo de hosts absurdos.
 */
class XtreamLoginTest {

    @Test
    fun `host sin esquema usa https por defecto`() {
        assertEquals(
            "https://example.com:8080/get.php?username=u&password=p&type=m3u_plus&output=ts",
            XtreamLogin.playlistUrl("example.com:8080", "u", "p"),
        )
        assertEquals(
            "https://example.com:8080/xmltv.php?username=u&password=p",
            XtreamLogin.epgUrl("example.com:8080", "u", "p"),
        )
    }

    @Test
    fun `host con esquema http se respeta`() {
        assertTrue(
            XtreamLogin.playlistUrl("http://example.com", "u", "p")
                .startsWith("http://example.com/get.php?"),
        )
    }

    @Test
    fun `barra final del host se recorta`() {
        assertEquals(
            "https://example.com/get.php?username=u&password=p&type=m3u_plus&output=ts",
            XtreamLogin.playlistUrl("example.com/", "u", "p"),
        )
    }

    @Test
    fun `credenciales se urlencoden`() {
        val url = XtreamLogin.playlistUrl("example.com", "us er", "p&s=")
        assertTrue(url.contains("username=us+er") || url.contains("username=us%20er"))
        assertTrue(!url.contains("p&s="))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `host vacío falla`() {
        XtreamLogin.playlistUrl("   ", "u", "p")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `host ridículo falla`() {
        XtreamLogin.playlistUrl("://", "u", "p")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `host con espacios falla`() {
        XtreamLogin.playlistUrl("exa mple.com", "u", "p")
    }
}
