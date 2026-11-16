package com.zabtv.app.ui.channels

import com.zabtv.app.data.db.GroupNameCount
import com.zabtv.app.data.db.ListEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fusión Guía→Canales (S1): sidebar puro con playlists + Todos + Historial +
 * grupos. Renombre de GuideSidebar sin cambio de comportamiento. TDD RED.
 */
class ChannelSidebarTest {

    private val lists = listOf(
        ListEntity(id = 1, name = "Casa", sourceUrl = "http://x/a.m3u"),
        ListEntity(id = 2, name = "Fuera", sourceUrl = "", portalHost = "example.com"),
    )
    private val groups = listOf(
        GroupNameCount("Deportes", 4),
        GroupNameCount("", 2),
    )

    @Test
    fun `orden playlists luego todos historial y grupos`() {
        val rows = ChannelSidebar.buildRows(lists, activeId = 1, groups, "Todos", "Historial", "Sin grupo")
        assertTrue(rows[0] is ChannelSidebar.Row.Playlist)
        assertTrue(rows[1] is ChannelSidebar.Row.Playlist)
        assertTrue(rows[2] is ChannelSidebar.Row.All)
        assertTrue(rows[3] is ChannelSidebar.Row.History)
        assertTrue(rows[4] is ChannelSidebar.Row.Group)
        assertTrue(rows[5] is ChannelSidebar.Row.Group)
        assertEquals(6, rows.size)
    }

    @Test
    fun `solo la activa va marcada`() {
        val rows = ChannelSidebar.buildRows(lists, activeId = 2, groups, "Todos", "Historial", "Sin grupo")
        val playlists = rows.filterIsInstance<ChannelSidebar.Row.Playlist>()
        assertEquals(listOf(false, true), playlists.map { it.active })
    }

    @Test
    fun `grupo sin nombre usa etiqueta sin grupo y todos suma`() {
        val rows = ChannelSidebar.buildRows(lists, activeId = 1, groups, "Todos", "Historial", "Sin grupo")
        val all = rows.filterIsInstance<ChannelSidebar.Row.All>().single()
        assertEquals(6, all.count)
        val blank = rows.filterIsInstance<ChannelSidebar.Row.Group>().single { it.name.isBlank() }
        assertEquals("Sin grupo", blank.label)
        assertEquals(2, blank.count)
    }

    @Test
    fun `historial lleva el conteo del cap`() {
        val rows = ChannelSidebar.buildRows(lists, activeId = 1, groups, "Todos", "Historial", "Sin grupo", historyCount = 3)
        assertEquals(3, rows.filterIsInstance<ChannelSidebar.Row.History>().single().count)
    }

    @Test
    fun `buildPlaylistRows solo playlists con la activa marcada`() {
        val rows = ChannelSidebar.buildPlaylistRows(lists, activeId = 2)
        assertEquals(2, rows.size)
        assertEquals(listOf(false, true), rows.map { it.active })
        assertEquals(listOf(1L, 2L), rows.map { it.id })
    }

    @Test
    fun `buildFilterRows solo filtros y grupos`() {
        val rows = ChannelSidebar.buildFilterRows(groups, "Todos", "Historial", "Sin grupo", historyCount = 3)
        assertTrue(rows[0] is ChannelSidebar.Row.All)
        assertTrue(rows[1] is ChannelSidebar.Row.History)
        assertTrue(rows[2] is ChannelSidebar.Row.Group)
        assertTrue(rows[3] is ChannelSidebar.Row.Group)
        assertEquals(4, rows.size)
        assertEquals(6, (rows[0] as ChannelSidebar.Row.All).count)
        assertEquals(3, (rows[1] as ChannelSidebar.Row.History).count)
    }

    @Test
    fun `buildRows delega en playlist mas filtros`() {
        val full = ChannelSidebar.buildRows(lists, activeId = 1, groups, "Todos", "Historial", "Sin grupo", historyCount = 3)
        val expected =
            ChannelSidebar.buildPlaylistRows(lists, activeId = 1) +
            ChannelSidebar.buildFilterRows(groups, "Todos", "Historial", "Sin grupo", historyCount = 3)
        assertEquals(expected, full)
    }
}
