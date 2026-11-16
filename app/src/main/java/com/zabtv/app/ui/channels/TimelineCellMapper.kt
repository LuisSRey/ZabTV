package com.zabtv.app.ui.channels

import com.zabtv.app.core.xmltv.GuideWindow
import com.zabtv.app.data.db.EpgProgramEntity

/**
 * Mapeo puro de celdas del timeline (S3): un título por corte de la
 * [window] o la etiqueta de vacío. Nunca muestra el nombre del canal — ni
 * siquiera lo recibe como parámetro.
 */
object TimelineCellMapper {

    /**
     * 6 celdas ([GuideWindow.slotCount]): el programa en el aire al inicio
     * del corte, si no el primero que empieza dentro, si no [emptyLabel].
     */
    fun mapCells(
        programs: List<EpgProgramEntity>,
        window: GuideWindow,
        emptyLabel: String,
    ): List<String> {
        val slots = window.slots()
        val ends = slots.map { it + window.slotMinutes * 60_000L }
        return slots.indices.map { i ->
            val title = programs.firstOrNull { it.startMillis <= slots[i] && it.endMillis > slots[i] }?.title
                ?: programs.firstOrNull { it.startMillis >= slots[i] && it.startMillis < ends[i] }?.title
                ?: ""
            title.ifBlank { emptyLabel }
        }
    }
}
