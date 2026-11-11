package com.tvplayer.app.ui.channels

import androidx.recyclerview.widget.RecyclerView
import com.tvplayer.app.core.xmltv.GuideWindow
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.EpgProgramEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CA4: triggers idle + orden ventana-primero + submitTimeline siempre
 * (mapa vacío incluido). El runner es JVM puro (sin Robolectric): el
 * fragment solo cablea vistas → runner.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserTimelineRunnerTest {

    private val window = GuideWindow(GuideWindow.anchorFor(1_794_294_600_000L))

    private fun channel(key: String, tvg: String = key) = ChannelEntity(
        listId = 7, tvgId = tvg, displayName = key, normName = key,
        groupName = "G", url = "https://s/$key", stableKey = key, position = 0,
    )

    private class Sink {
        val events = mutableListOf<String>()
        var lastWindow: GuideWindow? = null
        var lastTimeline: Map<String, List<EpgProgramEntity>>? = null
        var lastEpg: Map<String, String>? = null
        val setWindow: (GuideWindow) -> Unit = { w ->
            events += "setWindow"
            lastWindow = w
        }
        val submitEpg: (Map<String, String>) -> Unit = { m ->
            events += "submitEpg"
            lastEpg = m
        }
        val submitTimeline: (Map<String, List<EpgProgramEntity>>, GuideWindow) -> Unit = { m, w ->
            events += "submitTimeline"
            lastTimeline = m
            lastWindow = w
        }
    }

    private fun TestScope.runner(
        sink: Sink,
        range: IntRange? = 0..0,
        peeks: Map<Int, ChannelEntity> = mapOf(0 to channel("c1")),
        itemCount: Int = 1,
        batch: BrowserTimelineRunner.Batch = BrowserTimelineRunner.Batch(
            mapOf("c1" to "▶ Ahora"),
            mapOf("c1" to listOf(EpgProgramEntity(tvgId = "c1", startMillis = 1, endMillis = 2, title = "Ahora"))),
        ),
        retries: MutableList<Int> = mutableListOf(),
        debugs: MutableList<Pair<String, Int>> = mutableListOf(),
        clock: () -> Long = System::currentTimeMillis,
        batches: MutableList<Unit> = mutableListOf(),
    ) = BrowserTimelineRunner(
        scope = this,
        windowOf = { window },
        rangeOf = { range },
        peekOf = { peeks[it] },
        itemCountOf = { itemCount },
        batchOf = { batches += Unit; batch },
        setWindow = sink.setWindow,
        submitEpg = sink.submitEpg,
        submitTimeline = sink.submitTimeline,
        retryOf = { retries += it },
        debugOf = { reason, attempt -> debugs += reason to attempt },
        clockOf = clock,
    )

    @Test
    fun `predicados idle solo reposo y carga resuelta`() {
        assertTrue(BrowserTimelineRunner.fireOnScroll(RecyclerView.SCROLL_STATE_IDLE))
        assertTrue(!BrowserTimelineRunner.fireOnScroll(RecyclerView.SCROLL_STATE_DRAGGING))
        assertTrue(!BrowserTimelineRunner.fireOnScroll(RecyclerView.SCROLL_STATE_SETTLING))
        assertTrue(BrowserTimelineRunner.fireOnLoad(refreshNotLoading = true, itemCount = 3, history = false))
        assertTrue(!BrowserTimelineRunner.fireOnLoad(refreshNotLoading = false, itemCount = 3, history = false))
        assertTrue(!BrowserTimelineRunner.fireOnLoad(refreshNotLoading = true, itemCount = 0, history = false))
        assertTrue(!BrowserTimelineRunner.fireOnLoad(refreshNotLoading = true, itemCount = 3, history = true))
    }

    @Test
    fun `SCROLL_STATE_IDLE dispara fill y arrastrar no`() = runTest {
        val sink = Sink()
        val r = runner(sink)
        r.onScrollStateChanged(RecyclerView.SCROLL_STATE_DRAGGING)
        r.onScrollStateChanged(RecyclerView.SCROLL_STATE_SETTLING)
        advanceUntilIdle()
        assertTrue(sink.events.isEmpty())
        r.onScrollStateChanged(RecyclerView.SCROLL_STATE_IDLE)
        advanceUntilIdle()
        assertEquals(listOf("setWindow", "submitEpg", "submitTimeline"), sink.events)
        assertEquals(window, sink.lastWindow)
    }

    @Test
    fun `loadState NotLoading con items dispara e historial no`() = runTest {
        val sink = Sink()
        val r = runner(sink)
        r.onLoadState(refreshNotLoading = false, itemCount = 2, history = false)
        r.onLoadState(refreshNotLoading = true, itemCount = 0, history = false)
        r.onLoadState(refreshNotLoading = true, itemCount = 2, history = true)
        advanceUntilIdle()
        assertTrue(sink.events.isEmpty())
        r.onLoadState(refreshNotLoading = true, itemCount = 2, history = false)
        advanceUntilIdle()
        assertEquals(listOf("setWindow", "submitEpg", "submitTimeline"), sink.events)
    }

    @Test
    fun `setWindow va primero y submitTimeline cierra siempre`() = runTest {
        val sink = Sink()
        runner(sink).fill()
        advanceUntilIdle()
        assertEquals("setWindow", sink.events.first())
        assertEquals("submitTimeline", sink.events.last())
        // La tira va SIEMPRE (vacía incluida): sin submitEpg, una línea
        // rancia sobreviviría al lote vacío.
        val bare = Sink()
        runner(
            bare,
            batch = BrowserTimelineRunner.Batch(emptyMap(), mapOf("c1" to emptyList())),
        ).fill()
        advanceUntilIdle()
        assertEquals(listOf("setWindow", "submitEpg", "submitTimeline"), bare.events)
        assertEquals(emptyMap<String, String>(), bare.lastEpg)
        assertEquals(mapOf("c1" to emptyList<EpgProgramEntity>()), bare.lastTimeline)
    }

    @Test
    fun `retornos tempranos limpian tira y timeline`() = runTest {
        // Sin rango (layout sin resolver): tira + timeline vacíos.
        val noRange = Sink()
        runner(noRange, range = null).fill()
        advanceUntilIdle()
        assertEquals(listOf("setWindow", "submitEpg", "submitTimeline"), noRange.events)
        assertEquals(emptyMap<String, String>(), noRange.lastEpg)
        assertEquals(emptyMap<String, List<EpgProgramEntity>>(), noRange.lastTimeline)
        // Sin visibles (peek en curso, grilla cargada): lo mismo.
        val noPeek = Sink()
        val retries = mutableListOf<Int>()
        runner(noPeek, peeks = emptyMap(), itemCount = 2, retries = retries).fill()
        advanceUntilIdle()
        assertEquals(listOf("setWindow", "submitEpg", "submitTimeline"), noPeek.events)
        assertEquals(emptyMap<String, String>(), noPeek.lastEpg)
        assertTrue(noPeek.lastTimeline?.isEmpty() == true)
        assertEquals(listOf(1), retries)
    }

    @Test
    fun `sin posiciones visibles limpia con mapa vacio y reintenta`() = runTest {
        val sink = Sink()
        val retries = mutableListOf<Int>()
        runner(sink, range = null, retries = retries).fill()
        advanceUntilIdle()
        assertEquals(listOf("setWindow", "submitEpg", "submitTimeline"), sink.events)
        assertEquals(emptyMap<String, List<EpgProgramEntity>>(), sink.lastTimeline)
        assertEquals(listOf(1), retries)
    }

    @Test
    fun `peek vacio con grilla cargada limpia y reintenta`() = runTest {
        val sink = Sink()
        val retries = mutableListOf<Int>()
        runner(sink, peeks = emptyMap(), itemCount = 2, retries = retries).fill()
        advanceUntilIdle()
        assertTrue(sink.lastTimeline?.isEmpty() == true)
        assertEquals(listOf("setWindow", "submitEpg", "submitTimeline"), sink.events)
        assertEquals(listOf(1), retries)
    }

    @Test
    fun `peek vacio con grilla vacia limpia sin reintentar`() = runTest {
        val sink = Sink()
        val retries = mutableListOf<Int>()
        runner(sink, peeks = emptyMap(), itemCount = 0, retries = retries).fill()
        advanceUntilIdle()
        assertEquals(listOf("setWindow", "submitEpg", "submitTimeline"), sink.events)
        assertTrue(retries.isEmpty())
    }

    @Test
    fun `shouldFill puro debounce 300ms`() {
        assertEquals(300L, BrowserTimelineRunner.DEBOUNCE_MS)
        assertTrue(BrowserTimelineRunner.shouldFill(-300L, 0L))
        assertTrue(BrowserTimelineRunner.shouldFill(0L, 300L))
        assertTrue(!BrowserTimelineRunner.shouldFill(0L, 299L))
        assertTrue(BrowserTimelineRunner.shouldFill(1_000L, 1_300L))
        assertTrue(!BrowserTimelineRunner.shouldFill(1_000L, 1_299L))
    }

    @Test
    fun `fill tras debounce solo setWindow sin batch`() = runTest {
        var now = 10_000L
        val sink = Sink()
        val batches = mutableListOf<Unit>()
        val r = runner(sink, clock = { now }, batches = batches)
        r.fill()
        advanceUntilIdle()
        assertEquals(listOf("setWindow", "submitEpg", "submitTimeline"), sink.events)
        assertEquals(1, batches.size)
        // 100 ms después: debounceado — solo la ventana, sin batch ni
        // submits ni reintento.
        now += 100
        r.fill()
        advanceUntilIdle()
        assertEquals(
            listOf("setWindow", "submitEpg", "submitTimeline", "setWindow"),
            sink.events,
        )
        assertEquals(1, batches.size)
        // Tras la ventana de 300 ms el lote vuelve a correr.
        now += 300
        r.fill()
        advanceUntilIdle()
        assertEquals(2, batches.size)
        assertEquals(7, sink.events.size)
    }

    @Test
    fun `reintento acotado a max 1`() = runTest {
        assertEquals(1, BrowserTimelineRunner.MAX_RETRY)
        val sink = Sink()
        val retries = mutableListOf<Int>()
        runner(sink, range = null, retries = retries).fill()
        advanceUntilIdle()
        assertEquals(listOf(1), retries)
        // En el tope no hay reintento, pero tira y timeline se limpian.
        val capped = Sink()
        val cappedRetries = mutableListOf<Int>()
        runner(capped, range = null, retries = cappedRetries).fill(attempt = 1)
        advanceUntilIdle()
        assertTrue(cappedRetries.isEmpty())
        assertEquals(listOf("setWindow", "submitEpg", "submitTimeline"), capped.events)
    }

    @Test
    fun `retorno sin rango avisa a debugOf`() = runTest {
        val sink = Sink()
        val debugs = mutableListOf<Pair<String, Int>>()
        runner(sink, range = null, debugs = debugs).fill()
        advanceUntilIdle()
        assertEquals(listOf(BrowserTimelineRunner.EMPTY_NO_RANGE to 0), debugs)
    }

    @Test
    fun `retorno sin visibles avisa a debugOf`() = runTest {
        val sink = Sink()
        val debugs = mutableListOf<Pair<String, Int>>()
        runner(sink, peeks = emptyMap(), itemCount = 2, debugs = debugs).fill()
        advanceUntilIdle()
        assertEquals(listOf(BrowserTimelineRunner.EMPTY_NO_VISIBLES to 0), debugs)
    }

    @Test
    fun `lote con datos no avisa a debugOf`() = runTest {
        val sink = Sink()
        val debugs = mutableListOf<Pair<String, Int>>()
        runner(sink, debugs = debugs).fill()
        advanceUntilIdle()
        assertTrue(debugs.isEmpty())
    }
}
