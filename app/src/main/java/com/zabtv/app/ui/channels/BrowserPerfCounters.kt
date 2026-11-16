package com.zabtv.app.ui.channels

import java.util.concurrent.atomic.AtomicLong

/**
 * Contadores de rendimiento del browser (S4): fills del timeline, llamadas
 * al matcher y cargas/ahorros de logos Coil. Solo medición — ningún flujo
 * los lee para decidir. Hilo-seguro (el lote corre en background).
 */
object BrowserPerfCounters {

    private val fills = AtomicLong()
    private val matcherCalls = AtomicLong()
    private val coilLoads = AtomicLong()
    private val coilSkips = AtomicLong()

    fun incFills() {
        fills.incrementAndGet()
    }

    fun addMatcherCalls(n: Int) {
        if (n > 0) matcherCalls.addAndGet(n.toLong())
    }

    /** Logo pedido a Coil (tag distinto o vacío). */
    fun incCoilLoad() {
        coilLoads.incrementAndGet()
    }

    /** Logo reutilizado por tag-check (sin tocar red ni decoder). */
    fun incCoilSkip() {
        coilSkips.incrementAndGet()
    }

    fun snapshot(): Map<String, Long> = mapOf(
        "fills" to fills.get(),
        "matcherCalls" to matcherCalls.get(),
        "coilLoads" to coilLoads.get(),
        "coilSkips" to coilSkips.get(),
    )

    fun reset() {
        fills.set(0)
        matcherCalls.set(0)
        coilLoads.set(0)
        coilSkips.set(0)
    }
}
