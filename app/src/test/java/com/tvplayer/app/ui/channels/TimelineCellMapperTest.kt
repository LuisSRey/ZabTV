package com.tvplayer.app.ui.channels

import com.tvplayer.app.core.xmltv.GuideWindow
import com.tvplayer.app.data.db.EpgProgramEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3: mapeo puro de celdas del timeline — 6 items con títulos o "Sin datos",
 * nunca el nombre del canal (ni lo recibe como parámetro). TDD RED.
 */
class TimelineCellMapperTest {

    private val anchor = 1_794_294_600_000L // 2026-11-10T19:10:00Z, corte :30/:00
    private val window = GuideWindow(GuideWindow.anchorFor(anchor + 32L * 60_000L))
    private val empty = "Sin datos"

    @Test
    fun `sixCellsAlways con y sin guia`() {
        assertEquals(List(6) { empty }, TimelineCellMapper.mapCells(emptyList(), window, empty))
        val slots = window.slots()
        val programs = listOf(
            EpgProgramEntity(tvgId = "x", startMillis = slots[0], endMillis = slots[1], title = "Ahora"),
        )
        val cells = TimelineCellMapper.mapCells(programs, window, empty)
        assertEquals(6, cells.size)
        assertEquals("Ahora", cells[0])
        assertEquals(List(5) { empty }, cells.drop(1))
    }

    @Test
    fun `titulos en blanco son Sin datos nunca el canal`() {
        val slots = window.slots()
        val programs = listOf(
            EpgProgramEntity(tvgId = "x", startMillis = slots[0], endMillis = slots[1], title = "  "),
        )
        val cells = TimelineCellMapper.mapCells(programs, window, empty)
        assertEquals(List(6) { empty }, cells)
        assertTrue(cells.none { it == "Uno" })
    }

    @Test
    fun `sin guia ninguna celda muestra el nombre del canal`() {
        val cells = TimelineCellMapper.mapCells(emptyList(), window, empty)
        assertTrue(cells.none { it == "Uno" })
        assertTrue(cells.all { it == empty })
    }

    @Test
    fun `titulo EPG igual al displayName es legitimo no fuga`() {
        val slots = window.slots()
        val programs = listOf(
            EpgProgramEntity(tvgId = "x", startMillis = slots[0], endMillis = slots[1], title = "Uno"),
        )
        // El mapper ni recibe el nombre del canal: un título EPG que
        // coincide con el displayName se pinta, no se filtra como fuga.
        assertEquals("Uno", TimelineCellMapper.mapCells(programs, window, empty)[0])
    }

    @Test
    fun `reparte un titulo por corte como cellTitles`() {
        val slots = window.slots()
        val programs = listOf(
            EpgProgramEntity(tvgId = "x", startMillis = slots[0], endMillis = slots[1], title = "Primero"),
            EpgProgramEntity(tvgId = "x", startMillis = slots[2] + 60_000, endMillis = slots[4], title = "Tarde"),
        )
        assertEquals(
            listOf("Primero", empty, "Tarde", "Tarde", empty, empty),
            TimelineCellMapper.mapCells(programs, window, empty),
        )
    }
}
