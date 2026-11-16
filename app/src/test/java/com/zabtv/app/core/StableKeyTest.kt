package com.zabtv.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Clave estable del canal (spec F4): tvg-id si existe; si no, hash de
 * normName+group. La usan ImportService (upsert idempotente) y Favourites.
 */
class StableKeyTest {

    @Test
    fun `tvg-id manda`() {
        assertEquals("ch1.ext", StableKey.of("ch1.ext", "Canal Uno", "G"))
    }

    @Test
    fun `sin tvg-id usa sha1 de nombre y grupo`() {
        val a = StableKey.of(null, "Canal Uno", "General")
        val b = StableKey.of("", "Canal Uno", "General")
        assertEquals(a, b)
        assertNotEquals(a, StableKey.of(null, "Canal Dos", "General"))
        assertNotEquals(a, StableKey.of(null, "Canal Uno", "Otros"))
        assertEquals(40, a.length) // sha1 hex
    }

    @Test
    fun `normaliza antes de hashear`() {
        assertEquals(
            StableKey.of(null, "Canal Uno", "General"),
            StableKey.of(null, "Canal Uno FHD", "General"),
        )
    }
}
