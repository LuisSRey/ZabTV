package com.zabtv.app.ui.channels

import com.zabtv.app.data.db.EpgProgramEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * S4: caché de ventana EPG — una sola entrada (cap 1) por anchorMillis.
 * JVM puro.
 */
class EpgWindowCacheTest {

    private val anchor = 1_794_294_600_000L

    private fun entry(tag: String) = EpgWindowCache.Entry(
        nn = mapOf(tag to (null to null)),
        range = mapOf(tag to emptyList<EpgProgramEntity>()),
        approx = mapOf("k" to tag),
    )

    @Before
    fun clear() {
        EpgWindowCache.invalidate()
    }

    @Test
    fun `put y get con el mismo anchor`() {
        EpgWindowCache.put(anchor, entry("a"))
        assertEquals(entry("a"), EpgWindowCache.get(anchor))
    }

    @Test
    fun `otro anchor es miss (cap1 desaloja)`() {
        EpgWindowCache.put(anchor, entry("a"))
        assertNull(EpgWindowCache.get(anchor + 1L))
        EpgWindowCache.put(anchor + 1L, entry("b"))
        assertEquals(entry("b"), EpgWindowCache.get(anchor + 1L))
        // Cap 1: el anchor viejo ya no está.
        assertNull(EpgWindowCache.get(anchor))
    }

    @Test
    fun `invalidate limpia`() {
        EpgWindowCache.put(anchor, entry("a"))
        EpgWindowCache.invalidate()
        assertNull(EpgWindowCache.get(anchor))
    }
}
