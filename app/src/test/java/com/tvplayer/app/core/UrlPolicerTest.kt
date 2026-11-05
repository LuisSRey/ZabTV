package com.tvplayer.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec F8 (§6): allowlist de esquemas — media y logos SOLO http(s); se rechaza
 * file://, intent://, javascript:, content://, ftp://, user-info y caracteres peligrosos.
 */
class UrlPolicerTest {

    @Test
    fun `permite http y https`() {
        assertTrue(UrlPolicer.isAllowedHttpUrl("http://example.com/a.m3u8"))
        assertTrue(UrlPolicer.isAllowedHttpUrl("HTTPS://EXAMPLE.COM/OK"))
        assertTrue(UrlPolicer.isAllowedHttpUrl("https://host:8080/x?y=1"))
    }

    @Test
    fun `rechaza esquemas peligrosos y lios`() {
        for (bad in listOf<String?>(
            null, "", "notaurl", "file:///etc/passwd", "intent://foo", "javascript:alert(1)",
            "content://media/audio", "ftp://host/file", "HTTP://x" /* válida por esquema, pero ver caso abajo */,
        )) {
            if (bad != "HTTP://x") assertFalse("debería rechazar: $bad", UrlPolicer.isAllowedHttpUrl(bad))
        }
    }

    @Test
    fun `permite esquema en mayusculas`() {
        assertTrue(UrlPolicer.isAllowedHttpUrl("HTTP://EXAMPLE.COM/A.M3U8"))
    }

    @Test
    fun `rechaza user info y caracteres de control y espacios`() {
        assertFalse(UrlPolicer.isAllowedHttpUrl("http://user:pass@evil.com/s.m3u8"))
        assertFalse(UrlPolicer.isAllowedHttpUrl("http://exa mple.com/a"))
        assertFalse(UrlPolicer.isAllowedHttpUrl("http://evil.com/a\u0000b"))
        assertFalse(UrlPolicer.isAllowedHttpUrl("http://evil.com/a\n"))
    }
}
