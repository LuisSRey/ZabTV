package com.zabtv.app.ui.channels

import com.zabtv.app.core.xmltv.GuideWindow
import com.zabtv.app.data.db.ChannelEntity
import com.zabtv.app.data.db.EpgChannelEntity
import com.zabtv.app.data.db.EpgDao
import com.zabtv.app.data.db.EpgProgramEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S4: el loader cachea por anchor — la primera carga va a DB + matcher, la
 * segunda con el mismo anchor no repite queries (full-hit) y un anchor
 * nuevo vuelve a DB. Fake de [EpgDao] con conteo de llamadas. JVM puro.
 */
class EpgBatchLoaderTest {

    private val now = 1_793_880_000_000L
    private val window = GuideWindow.now(now)

    private class FakeEpgDao : EpgDao {
        val nowNextCalls = mutableListOf<List<String>>()
        val rangeCalls = mutableListOf<List<String>>()
        var hasProgramsCalls = 0
        var catalogCalls = 0
        var programs: List<EpgProgramEntity> = emptyList()
        var catalog: List<EpgChannelEntity> = emptyList()

        override suspend fun nowRows(tvgIds: List<String>, atMillis: Long) =
            programs.filter { it.tvgId in tvgIds && it.startMillis <= atMillis && it.endMillis > atMillis }

        override suspend fun nextRows(tvgIds: List<String>, atMillis: Long) =
            tvgIds.mapNotNull { id ->
                programs.filter { it.tvgId == id && it.startMillis > atMillis }
                    .minByOrNull { it.startMillis }
            }

        @androidx.room.Transaction
        override suspend fun nowNext(
            tvgIds: List<String>,
            atMillis: Long,
        ): Map<String, Pair<EpgProgramEntity?, EpgProgramEntity?>> {
            nowNextCalls += tvgIds.toList()
            return super.nowNext(tvgIds, atMillis)
        }

        override suspend fun programsInRange(
            tvgIds: List<String>,
            startMillis: Long,
            endMillis: Long,
        ): List<EpgProgramEntity> {
            rangeCalls += tvgIds.toList()
            return programs.filter { it.tvgId in tvgIds && it.endMillis > startMillis && it.startMillis < endMillis }
        }

        override suspend fun hasPrograms(tvgId: String): Boolean {
            hasProgramsCalls++
            return programs.any { it.tvgId == tvgId }
        }

        override suspend fun allEpgChannels(): List<EpgChannelEntity> {
            catalogCalls++
            return catalog
        }

        override suspend fun insertBatch(programs: List<EpgProgramEntity>) = Unit
        override suspend fun count(): Int = 0
        override suspend fun dayPrograms(tvgId: String, startInclMillis: Long, endInclMillis: Long) = emptyList<EpgProgramEntity>()
        override suspend fun pruneBefore(beforeMillis: Long) = 0
        override suspend fun clearAll() = Unit
        override suspend fun insertChannels(channels: List<EpgChannelEntity>) = Unit
    }

    private fun channel(key: String, tvg: String? = key, name: String = key) = ChannelEntity(
        listId = 7, tvgId = tvg, displayName = name, normName = name.lowercase(),
        groupName = "G", url = "https://s/$key", stableKey = key, position = 0,
    )

    private fun program(tvg: String, title: String) = EpgProgramEntity(
        tvgId = tvg, startMillis = now - 3_600_000L, endMillis = now + 3_600_000L, title = title,
    )

    @Before
    fun clearCache() {
        EpgWindowCache.invalidate()
    }

    @Test
    fun `primera carga va a DB y la segunda con mismo anchor es full-hit sin queries`() = runTest {
        val dao = FakeEpgDao().apply {
            programs = listOf(program("c1", "Ahora"), program("c2", "Otro"))
        }
        val loader = EpgBatchLoader(dao) { ids, w ->
            dao.programsInRange(ids, w.startMillis, w.endMillis).groupBy { it.tvgId }
        }
        val visibles = listOf(channel("c1"), channel("c2"))
        val first = loader.load(visibles, window, now)
        assertEquals(false, first.cacheHit)
        assertEquals(2, first.range.size)
        val callsAfterFirst = dao.nowNextCalls.size + dao.rangeCalls.size
        assertTrue(callsAfterFirst > 0)

        val second = loader.load(visibles, window, now)
        assertEquals(true, second.cacheHit)
        assertEquals(first.range, second.range)
        assertEquals(first.nn.keys, second.nn.keys)
        assertEquals(callsAfterFirst, dao.nowNextCalls.size + dao.rangeCalls.size)
        assertEquals(0, second.matcherCalls)
    }

    @Test
    fun `anchor nuevo vuelve a DB`() = runTest {
        val dao = FakeEpgDao().apply {
            programs = listOf(program("c1", "Ahora"))
        }
        val loader = EpgBatchLoader(dao) { ids, w ->
            dao.programsInRange(ids, w.startMillis, w.endMillis).groupBy { it.tvgId }
        }
        val visibles = listOf(channel("c1"))
        loader.load(visibles, window, now)
        val calls = dao.nowNextCalls.size + dao.rangeCalls.size
        val other = GuideWindow(window.anchorMillis + 30L * 60_000L)
        val again = loader.load(visibles, other, now + 30L * 60_000L)
        assertEquals(false, again.cacheHit)
        assertTrue(dao.nowNextCalls.size + dao.rangeCalls.size > calls)
    }

    @Test
    fun `visibles nuevos con mismo anchor solo consultan lo nuevo`() = runTest {
        val dao = FakeEpgDao().apply {
            programs = listOf(program("c1", "Uno"), program("c2", "Dos"))
        }
        val loader = EpgBatchLoader(dao) { ids, w ->
            dao.programsInRange(ids, w.startMillis, w.endMillis).groupBy { it.tvgId }
        }
        loader.load(listOf(channel("c1")), window, now)
        dao.nowNextCalls.clear()
        dao.rangeCalls.clear()
        val second = loader.load(listOf(channel("c1"), channel("c2")), window, now)
        assertEquals(false, second.cacheHit)
        assertTrue(dao.nowNextCalls.flatten().contains("c2"))
        assertTrue(dao.nowNextCalls.flatten().none { it == "c1" })
        assertEquals(setOf("c1", "c2"), second.range.keys)
    }

    @Test
    fun `matcherCalls cuenta misses y full-hit no matchea`() = runTest {
        val dao = FakeEpgDao().apply {
            programs = listOf(program("c1", "Ahora"))
            catalog = listOf(EpgChannelEntity(tvgId = "xml.9", displayName = "Canal Nueve"))
        }
        val loader = EpgBatchLoader(dao) { ids, w ->
            dao.programsInRange(ids, w.startMillis, w.endMillis).groupBy { it.tvgId }
        }
        // "Otra" no tiene guía exacta ni aproximada (el catálogo no calza).
        val first = loader.load(listOf(channel("c1"), channel("otra", name = "Otra")), window, now)
        assertTrue(first.matcherCalls >= 1)
        val second = loader.load(listOf(channel("c1"), channel("otra", name = "Otra")), window, now)
        assertEquals(true, second.cacheHit)
        assertEquals(0, second.matcherCalls)
    }
}
