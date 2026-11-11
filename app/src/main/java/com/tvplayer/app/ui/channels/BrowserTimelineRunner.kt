package com.tvplayer.app.ui.channels

import androidx.recyclerview.widget.RecyclerView
import com.tvplayer.app.core.xmltv.GuideWindow
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.EpgProgramEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Orquestación testeable del timeline del browser (CA4): la ventana va
 * SIEMPRE primero ([setWindow] antes de cualquier salida) y el
 * [submitTimeline] va SIEMPRE (mapa vacío incluido) para limpiar lotes
 * rancios. Los triggers idle (scroll quieto, carga resuelta con items)
 * viven aquí para test JVM directo; el fragment solo cablea vistas →
 * runner y aporta el lote ([batchOf] con DB + ViewModel).
 */
internal class BrowserTimelineRunner(
    private val scope: CoroutineScope,
    private val windowOf: () -> GuideWindow,
    private val rangeOf: () -> IntRange?,
    private val peekOf: (Int) -> ChannelEntity?,
    private val itemCountOf: () -> Int,
    private val batchOf: suspend (List<ChannelEntity>) -> Batch,
    private val setWindow: (GuideWindow) -> Unit,
    private val submitEpg: (Map<String, String>) -> Unit,
    private val submitTimeline: (Map<String, List<EpgProgramEntity>>, GuideWindow) -> Unit,
    private val retryOf: (Int) -> Unit,
    private val maxRetry: Int = MAX_RETRY,
    private val debugOf: (reason: String, attempt: Int) -> Unit = { _, _ -> },
) {

    /** Lote por visibles: tira ahora/siguiente + programas de la ventana. */
    data class Batch(
        val epgLines: Map<String, String>,
        val perChannel: Map<String, List<EpgProgramEntity>>,
    )

    companion object {
        /** Tope de reintentos tras grid sin visibles (mismo que el fragment). */
        const val MAX_RETRY = 2

        /** Motivos de retorno vacío para la traza ([TimelineDebug]). */
        const val EMPTY_NO_RANGE = "sin-rango"
        const val EMPTY_NO_VISIBLES = "sin-visibles"

        /** Predicado del trigger de scroll (puro): solo el reposo rellena. */
        fun fireOnScroll(newState: Int): Boolean =
            newState == RecyclerView.SCROLL_STATE_IDLE

        /**
         * Predicado del trigger de carga (puro): refresh resuelto + items +
         * no-historial (el Historial es snapshot y lo resuelve reloadContent).
         */
        fun fireOnLoad(refreshNotLoading: Boolean, itemCount: Int, history: Boolean): Boolean =
            !history && refreshNotLoading && itemCount > 0
    }

    /** Pausa de scroll: solo el reposo re-dispara el timeline. */
    fun onScrollStateChanged(newState: Int) {
        if (fireOnScroll(newState)) fill()
    }

    /** Carga resuelta con items (no-historial): re-dispara el timeline. */
    fun onLoadState(refreshNotLoading: Boolean, itemCount: Int, history: Boolean) {
        if (fireOnLoad(refreshNotLoading, itemCount, history)) fill()
    }

    /**
     * Doble batch sobre los visibles → líneas "▶ ahora · después" (submitEpg)
     * + programas de la ventana por canal (submitTimeline). La ventana va
     * SIEMPRE primero; AMBOS submits van SIEMPRE (vacíos incluidos) para
     * limpiar lotes rancios. Sin visibles (layout sin resolver o peek en
     * curso) reintento acotado.
     */
    fun fill(attempt: Int = 0) {
        val window = windowOf()
        setWindow(window)
        val range = rangeOf()
        if (range == null || range.first < 0 || range.last < range.first) {
            if (attempt < maxRetry) retryOf(attempt + 1)
            debugOf(EMPTY_NO_RANGE, attempt)
            submitEpg(emptyMap())
            submitTimeline(emptyMap(), window)
            return
        }
        scope.launch {
            val visibles = range.mapNotNull(peekOf)
            if (visibles.isEmpty()) {
                if (itemCountOf() > 0 && attempt < maxRetry) retryOf(attempt + 1)
                debugOf(EMPTY_NO_VISIBLES, attempt)
                submitEpg(emptyMap())
                submitTimeline(emptyMap(), window)
                return@launch
            }
            val batch = batchOf(visibles)
            submitEpg(batch.epgLines)
            submitTimeline(batch.perChannel, window)
        }
    }
}
