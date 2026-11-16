package com.zabtv.app.ui.channels

import com.zabtv.app.BuildConfig
import com.zabtv.app.core.xmltv.GuideWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G2 S5: traza del timeline sin PII — solo contadores + tvgId + anchor +
 * slots + visibilidades + versión APK; Log.d solo tras flag debug + tag
 * habilitado. JVM puro (gates inyectables, sin Robolectric).
 */
class TimelineDebugTest {

    private val window = GuideWindow(GuideWindow.anchorFor(1_794_294_600_000L))
    private val vis = "hero=G slots=V empty=G"

    @Test
    fun `batchMessage lleva contadores tvgId anchor slots visibilidades y version`() {
        val msg = TimelineDebug.batchMessage(
            tvgIds = listOf("c1", "c2"),
            anchorMillis = window.anchorMillis,
            slotCount = window.slotCount,
            visibles = 2, epg = 1, channels = 1,
            vis = vis,
        )
        assertTrue(msg.contains("c1") && msg.contains("c2"))
        assertTrue(msg.contains("anchor=${window.anchorMillis}"))
        assertTrue(msg.contains("slots=6"))
        assertTrue(msg.contains("visibles=2") && msg.contains("epg=1") && msg.contains("canales=1"))
        assertTrue(msg.contains("[$vis]"))
        assertTrue(msg.contains(BuildConfig.VERSION_NAME))
    }

    @Test
    fun `batchMessage jamas lleva titulos nombres ni urls`() {
        val msg = TimelineDebug.batchMessage(
            tvgIds = listOf("c1"),
            anchorMillis = window.anchorMillis,
            slotCount = window.slotCount,
            visibles = 1, epg = 0, channels = 0,
            vis = vis,
        )
        assertFalse(msg.contains("El Gran Show"))
        assertFalse(msg.contains("Mi Canal Favorito"))
        assertFalse(msg.contains("https://"))
        assertFalse(msg.contains("acestream://"))
    }

    @Test
    fun `logBatch no emite sin debug`() {
        var emitted = 0
        TimelineDebug.logBatch(
            tvgIds = listOf("c1"), window = window,
            visibles = 1, epg = 0, channels = 0, vis = vis,
            debug = false, isLoggable = { true }, emit = { emitted++ },
        )
        assertEquals(0, emitted)
    }

    @Test
    fun `logBatch no emite sin tag habilitado`() {
        var emitted = 0
        TimelineDebug.logBatch(
            tvgIds = listOf("c1"), window = window,
            visibles = 1, epg = 0, channels = 0, vis = vis,
            debug = true, isLoggable = { false }, emit = { emitted++ },
        )
        assertEquals(0, emitted)
    }

    @Test
    fun `logBatch emite con debug y tag`() {
        val out = mutableListOf<String>()
        TimelineDebug.logBatch(
            tvgIds = listOf("c1"), window = window,
            visibles = 1, epg = 0, channels = 0, vis = vis,
            debug = true, isLoggable = { true }, emit = { out += it },
        )
        assertEquals(
            listOf(
                TimelineDebug.batchMessage(
                    tvgIds = listOf("c1"),
                    anchorMillis = window.anchorMillis,
                    slotCount = window.slotCount,
                    visibles = 1, epg = 0, channels = 0, vis = vis,
                ),
            ),
            out,
        )
    }

    @Test
    fun `batchMessage lleva cache hit-miss`() {
        val base = TimelineDebug.batchMessage(
            tvgIds = listOf("c1"),
            anchorMillis = window.anchorMillis,
            slotCount = window.slotCount,
            visibles = 1, epg = 1, channels = 1, vis = vis,
        )
        assertTrue(base.contains("cache=miss"))
        val hit = TimelineDebug.batchMessage(
            tvgIds = listOf("c1"),
            anchorMillis = window.anchorMillis,
            slotCount = window.slotCount,
            visibles = 1, epg = 1, channels = 1, vis = vis,
            cacheHit = true,
        )
        assertTrue(hit.contains("cache=hit"))
    }

    @Test
    fun `logEmpty emite motivo e intento, y calla sin gates`() {
        val out = mutableListOf<String>()
        TimelineDebug.logEmpty(
            reason = BrowserTimelineRunner.EMPTY_NO_RANGE, attempt = 1, window = window,
            debug = true, isLoggable = { true }, emit = { out += it },
        )
        assertEquals(1, out.size)
        assertTrue(out[0].contains(BrowserTimelineRunner.EMPTY_NO_RANGE))
        assertTrue(out[0].contains("intento=1"))
        assertTrue(out[0].contains("anchor=${window.anchorMillis}"))
        assertTrue(out[0].contains(BuildConfig.VERSION_NAME))
        var silent = 0
        TimelineDebug.logEmpty(
            reason = "x", attempt = 0, window = window,
            debug = false, isLoggable = { true }, emit = { silent++ },
        )
        TimelineDebug.logEmpty(
            reason = "x", attempt = 0, window = window,
            debug = true, isLoggable = { false }, emit = { silent++ },
        )
        assertEquals(0, silent)
    }
}
