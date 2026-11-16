package com.zabtv.app.ui.channels

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * S4: contadores de rendimiento del browser (fills, matcher, Coil).
 * JVM puro.
 */
class BrowserPerfCountersTest {

    @Before
    fun reset() {
        BrowserPerfCounters.reset()
    }

    @Test
    fun `arrancan en cero`() {
        assertEquals(
            mapOf("fills" to 0L, "matcherCalls" to 0L, "coilLoads" to 0L, "coilSkips" to 0L),
            BrowserPerfCounters.snapshot(),
        )
    }

    @Test
    fun `inc y add acumulan y reset limpia`() {
        BrowserPerfCounters.incFills()
        BrowserPerfCounters.incFills()
        BrowserPerfCounters.addMatcherCalls(3)
        BrowserPerfCounters.incCoilLoad()
        BrowserPerfCounters.incCoilSkip()
        BrowserPerfCounters.incCoilSkip()
        assertEquals(
            mapOf("fills" to 2L, "matcherCalls" to 3L, "coilLoads" to 1L, "coilSkips" to 2L),
            BrowserPerfCounters.snapshot(),
        )
        BrowserPerfCounters.reset()
        assertEquals(
            mapOf("fills" to 0L, "matcherCalls" to 0L, "coilLoads" to 0L, "coilSkips" to 0L),
            BrowserPerfCounters.snapshot(),
        )
    }
}
