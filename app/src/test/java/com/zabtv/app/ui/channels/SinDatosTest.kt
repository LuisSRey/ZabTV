package com.zabtv.app.ui.channels

import com.zabtv.app.core.xmltv.GuideWindow
import com.zabtv.app.data.db.EpgProgramEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3: sin guía no se inventa nada — sin tvg-id (o sin programas para la
 * clave resuelta) las 6 celdas llevan la etiqueta de vacío; con guía
 * parcial se mezclan títulos y vacíos. JVM puro.
 */
class SinDatosTest {

    private val anchor = GuideWindow.anchorFor(1_794_294_600_000L)
    private val window = GuideWindow(anchor)
    private val empty = "Sin datos"

    private fun program(tvg: String, start: Long, end: Long, title: String) =
        EpgProgramEntity(tvgId = tvg, startMillis = start, endMillis = end, title = title)

    @Test
    fun `sin tvg-id no hay clave y todo es emptyLabel`() {
        val slots = window.slots()
        val range = mapOf(
            "otro" to listOf(program("otro", slots[0], slots[1], "Vecino")),
        )
        assertNull(BrowserEpgResolver.resolveKey(null, range, null))
        assertNull(BrowserEpgResolver.resolveKey("", range, null))
        assertNull(BrowserEpgResolver.resolveKey("   ", range, null))
        // Sin clave no hay programas del canal: las 6 celdas van a vacío.
        assertEquals(List(6) { empty }, TimelineCellMapper.mapCells(emptyList(), window, empty))
    }

    @Test
    fun `sin programas para la clave todo es emptyLabel`() {
        val slots = window.slots()
        val range = mapOf(
            "otro" to listOf(program("otro", slots[0], slots[1], "Vecino")),
        )
        // Ni exacta ("mio" no está en el rango) ni aproximada sin programas.
        assertNull(BrowserEpgResolver.resolveKey("mio", range, null))
        assertNull(BrowserEpgResolver.resolveKey("mio", range, "aproxSinGuia"))
        assertEquals(List(6) { empty }, TimelineCellMapper.mapCells(emptyList(), window, empty))
    }

    @Test
    fun `guia parcial mezcla titulos y vacio`() {
        val slots = window.slots()
        val programs = listOf(
            program("mio", slots[0], slots[1], "Primero"),
            program("mio", slots[3] + 60_000, slots[5], "Tarde"),
        )
        assertEquals(
            listOf("Primero", empty, empty, "Tarde", "Tarde", empty),
            TimelineCellMapper.mapCells(programs, window, empty),
        )
    }

    @Test
    fun `titulos en blanco son vacio nunca canal`() {
        val slots = window.slots()
        val programs = listOf(
            program("mio", slots[2], slots[3], "  "),
        )
        val cells = TimelineCellMapper.mapCells(programs, window, empty)
        assertEquals(List(6) { empty }, cells)
        assertTrue(cells.none { it.isNotBlank() && it != empty })
    }
}
