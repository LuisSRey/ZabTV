package com.tvplayer.app.ui.channels

import com.tvplayer.app.data.db.GroupNameCount
import com.tvplayer.app.data.db.ListEntity

/**
 * Sidebar del browser: playlists + Todos + Historial + grupos. Puro JVM (sin
 * Android) para test directo. Room sigue siendo el único estado: esto solo
 * ordena filas para pintar; la selección vive en el fragment y la lista
 * activa global en [com.tvplayer.app.data.ActiveList].
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

    fun buildRows(
        lists: List<ListEntity>,
        activeId: Long?,
        groups: List<GroupNameCount>,
        allLabel: String,
        historyLabel: String,
        noGroupLabel: String,
        historyCount: Int = 0,
    ): List<Row> {
        val rows = ArrayList<Row>(lists.size + groups.size + 2)
        for (list in lists) {
            rows += Row.Playlist(list.id, list.name, list.id == activeId)
        }
        rows += Row.All(allLabel, groups.sumOf { it.count })
        rows += Row.History(historyLabel, historyCount)
        for (group in groups) {
            rows += Row.Group(group.group, group.group.ifBlank { noGroupLabel }, group.count)
        }
        return rows
    }
}
