package com.tvplayer.app.core.xmltv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guía S3: ventana propia 3 h en cortes de 30 min. TDD RED.
 */
class GuideWindowTest {

    @Test
    fun `anchor trunca al corte anterior`() {
        // 2026-11-10T19:42:00Z → 19:30:00Z
        val t = 1_794_296_520_000L
        val anchor = GuideWindow.anchorFor(t)
        assertEquals(t - 12L * 60_000L, anchor)
        assertEquals(0L, anchor % (30L * 60_000L))
    }

    @Test
    fun `ventana por defecto son 6 cortes de 30 min`() {
        val w = GuideWindow(1_000_000_000_000L)
        assertEquals(6, w.slots().size)
        assertEquals(3L * 3_600_000L, w.endMillis - w.startMillis)
        assertEquals(w.startMillis + 30L * 60_000L, w.slots()[1])
    }

    @Test
    fun `progress se clampa a 0-1`() {
        assertEquals(0f, GuideWindow.progress(0, 100, 200))
        assertEquals(0.5f, GuideWindow.progress(150, 100, 200))
        assertEquals(1f, GuideWindow.progress(999, 100, 200))
        assertEquals(0f, GuideWindow.progress(150, 200, 200))
        assertTrue(GuideWindow.progress(150, 100, 200) in 0f..1f)
    }
}
