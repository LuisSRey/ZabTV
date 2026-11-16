package com.zabtv.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S1: el <queries> declara los handlers externos de HLS/DASH
 * (http/https × application/vnd.apple.mpegurl y application/dash+xml):
 * sin esto Android 11+ oculta a los reproductores que filtran por mimeType
 * y el auto-route cae a "sin app" aunque haya reproductor instalado.
 * Sin permisos nuevos, sin usesCleartextTraffic.
 */
class ExternalQueriesTest {

    private fun manifest(): String {
        val f = File("src/main/AndroidManifest.xml")
        assertTrue("falta AndroidManifest.xml", f.exists())
        return f.readText()
    }

    private fun queriesBlock(xml: String): String {
        val open = xml.indexOf("<queries>")
        val close = xml.indexOf("</queries>")
        assertTrue("falta bloque <queries>", open >= 0 && close > open)
        return xml.substring(open, close)
    }

    @Test
    fun `queries trae http y https por mpegurl y dash`() {
        val q = queriesBlock(manifest())
        val combos = listOf(
            "http" to "application/vnd.apple.mpegurl",
            "https" to "application/vnd.apple.mpegurl",
            "http" to "application/dash+xml",
            "https" to "application/dash+xml",
        )
        for ((scheme, mime) in combos) {
            val re = Regex(
                "<intent>\\s*<action android:name=\"android.intent.action.VIEW\" />\\s*" +
                    "<data android:scheme=\"$scheme\" android:mimeType=\"\\Q$mime\\E\" />\\s*</intent>",
            )
            assertTrue("falta intent VIEW $scheme + $mime", re.containsMatchIn(q))
        }
    }

    @Test
    fun `sin permisos nuevos ni cleartext`() {
        val xml = manifest()
        val perms = Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"[^>]*/?>")
            .findAll(xml).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf("android.permission.INTERNET", "android.permission.READ_EXTERNAL_STORAGE"),
            perms,
        )
        assertFalse(xml.contains("usesCleartextTraffic"))
    }
}
