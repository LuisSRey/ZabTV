package com.tvplayer.app.ui.channels

import com.tvplayer.app.data.db.EpgProgramEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S3: BrowserEpgResolver — clave EPG a pintar por canal: exacta con programas
 * crudos → fallback aproximado (EpgMatcher) → null sin guía. Nunca "".
 * Puro JVM (el caller aporta el rango ya consultado y el hit aproximado).
 */
class BrowserEpgResolverTest {

    private fun prog(tvgId: String, title: String) = EpgProgramEntity(
        tvgId = tvgId, startMillis = 1_000, endMillis = 2_000, title = title,
    )

    @Test
    fun `exacto con programas gana sobre aproximado`() {
        val range = mapOf(
            "c1" to listOf(prog("c1", "Exacto")),
            "aprox" to listOf(prog("aprox", "Aprox")),
        )
        assertEquals("c1", BrowserEpgResolver.resolveKey("c1", range, "aprox"))
    }

    @Test
    fun `sin exacto usa el fallback aproximado`() {
        val range = mapOf("aprox" to listOf(prog("aprox", "Aprox")))
        assertEquals("aprox", BrowserEpgResolver.resolveKey("c1", range, "aprox"))
    }

    @Test
    fun `sin tvgId usa el fallback aproximado`() {
        val range = mapOf("aprox" to listOf(prog("aprox", "Aprox")))
        assertEquals("aprox", BrowserEpgResolver.resolveKey(null, range, "aprox"))
        assertEquals("aprox", BrowserEpgResolver.resolveKey("  ", range, "aprox"))
    }

    @Test
    fun `exacto sin programas en rango cae al fallback`() {
        val range = mapOf(
            "c1" to emptyList(),
            "aprox" to listOf(prog("aprox", "Aprox")),
        )
        assertEquals("aprox", BrowserEpgResolver.resolveKey("c1", range, "aprox"))
    }

    @Test
    fun `sin guia devuelve null nunca vacio`() {
        assertNull(BrowserEpgResolver.resolveKey("c1", emptyMap(), null))
        assertNull(BrowserEpgResolver.resolveKey("c1", emptyMap(), ""))
        assertNull(BrowserEpgResolver.resolveKey("c1", emptyMap(), "  "))
        assertNull(BrowserEpgResolver.resolveKey(null, emptyMap(), null))
        assertNull(BrowserEpgResolver.resolveKey("", emptyMap(), ""))
        // Aproximado sin programas en rango tampoco vale: null, no "".
        val range = mapOf("aprox" to emptyList<EpgProgramEntity>())
        assertNull(BrowserEpgResolver.resolveKey("c1", range, "aprox"))
    }

    @Test
    fun `aproximado igual al exacto no duplica`() {
        val range = mapOf("c1" to emptyList<EpgProgramEntity>())
        assertNull(BrowserEpgResolver.resolveKey("c1", range, "c1"))
    }
}
