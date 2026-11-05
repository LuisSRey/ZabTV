package com.tvplayer.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizerTest {

    @Test
    fun `lowercase sin acentos tokens fuera`() {
        assertEquals("canal espanol", Normalizer.normName("Canal HD  Español™"))
    }

    @Test
    fun `quita tokens de calidad como palabras completas`() {
        assertEquals("espn", Normalizer.normName("ESPN 4K UHD"))
        assertEquals("espn", Normalizer.normName("ESPN FHD"))
        assertEquals("espn", Normalizer.normName("ESPN 1080P HD"))
    }

    @Test
    fun `deja el resto intacto excepto espacios`() {
        assertEquals("atv 2", Normalizer.normName("ÁTV-2 FHD"))
    }

    @Test
    fun `empty y null safe`() {
        assertEquals("", Normalizer.normName(null))
        assertEquals("", Normalizer.normName(""))
    }
}
