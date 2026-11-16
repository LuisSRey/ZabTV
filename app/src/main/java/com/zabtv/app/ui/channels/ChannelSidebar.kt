package com.zabtv.app.ui.channels

import com.zabtv.app.data.db.GroupNameCount
import com.zabtv.app.data.db.ListEntity

/**
 * Sidebar del browser: playlists + Todos + Historial + grupos. Puro JVM (sin
 * Android) para test directo. Room sigue siendo el único estado: esto solo
 * ordena filas para pintar; la selección vive en el fragment y la lista
 * activa global en [com.zabtv.app.data.ActiveList].
 *
 * Fusión Guía→Canales (S1): renombre de GuideSidebar sin cambio de
 * comportamiento.
 */
object ChannelSidebar {

    /** Filtro dentro de la lista activa (`Group(null)` = todos, como antes). */
    sealed interface Selection {
        data object All : Selection
        data object History : Selection
        data class Group(val name: String?) : Selection
    }

    /** Fila pintable: playlists primero, luego filtros y grupos. */
    sealed interface Row {
        data class Playlist(val id: Long, val name: String, val active: Boolean) : Row
        data class All(val label: String, val count: Int) : Row
        data class History(val label: String, val count: Int) : Row
        data class Group(val name: String, val label: String, val count: Int) : Row
    }

    /** Playlists para el dropdown móvil (el rail móvil solo lleva filtros). */
    fun buildPlaylistRows(
        lists: List<ListEntity>,
        activeId: Long?,
    ): List<Row.Playlist> =
        lists.map { Row.Playlist(it.id, it.name, it.id == activeId) }

    /** Filtros + grupos para el rail (móvil y TV comparten este bloque). */
    fun buildFilterRows(
        groups: List<GroupNameCount>,
        allLabel: String,
        historyLabel: String,
        noGroupLabel: String,
        historyCount: Int = 0,
    ): List<Row> {
        val rows = ArrayList<Row>(groups.size + 2)
        rows += Row.All(allLabel, groups.sumOf { it.count })
        rows += Row.History(historyLabel, historyCount)
        for (group in groups) {
            rows += Row.Group(group.group, group.group.ifBlank { noGroupLabel }, group.count)
        }
        return rows
    }

    fun buildRows(
        lists: List<ListEntity>,
        activeId: Long?,
        groups: List<GroupNameCount>,
        allLabel: String,
        historyLabel: String,
        noGroupLabel: String,
        historyCount: Int = 0,
    ): List<Row> =
        buildPlaylistRows(lists, activeId) +
            buildFilterRows(groups, allLabel, historyLabel, noGroupLabel, historyCount)
}
