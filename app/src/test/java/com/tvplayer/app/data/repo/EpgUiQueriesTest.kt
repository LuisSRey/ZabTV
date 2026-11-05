package com.tvplayer.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.data.db.AppDatabase
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.EpgChannelEntity
import com.tvplayer.app.data.db.EpgProgramEntity
import com.tvplayer.app.data.db.ListEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 14: EpgMatcher resuelve la clave EPG de un canal — exacta por tvg-id o
 * aproximada por nombre normalizado (fallback marcado) — y dayPrograms
 * devuelve el rango del día de forma determinista.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpgUiQueriesTest {

    private lateinit var db: AppDatabase
    private lateinit var matcher: EpgMatcher

    private val now = 1_793_880_000_000L // 2026-11-05T12:00:00Z
    private val h = 3_600_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        matcher = EpgMatcher(db.epgDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun channel(tvgId: String?, name: String, group: String = "G") = ChannelEntity(
        listId = 1L, tvgId = tvgId, displayName = name, normName = name.lowercase(),
        groupName = group, url = "http://s/x.m3u8", stableKey = tvgId ?: name, position = 0,
    )

    @Test
    fun `match exacto por tvg-id con programas`() = runTest {
        db.epgDao().insertBatch(
            listOf(EpgProgramEntity(tvgId = "c1.ext", startMillis = now - h, endMillis = now + h, title = "Ahora")),
        )
        val match = matcher.match(channel("c1.ext", "Canal Uno"))
        assertEquals("c1.ext", match!!.tvgId)
        assertFalse(match.approximate)
    }

    @Test
    fun `sin tvg-id hace fallback por nombre y lo marca aproximado`() = runTest {
        db.epgDao().insertChannels(
            listOf(EpgChannelEntity(tvgId = "xml.1", displayName = "Canal Dos HD")),
        )
        db.epgDao().insertBatch(
            listOf(EpgProgramEntity(tvgId = "xml.1", startMillis = now - h, endMillis = now + h, title = "Ahora")),
        )
        val match = matcher.match(channel(null, "Canal Dos"))
        assertEquals("xml.1", match!!.tvgId)
        assertTrue(match.approximate)
    }

    @Test
    fun `sin programas en ningún lado devuelve null`() = runTest {
        assertNull(matcher.match(channel("c9", "Nadie")))
        assertNull(matcher.match(channel(null, "Tampoco")))
    }

    @Test
    fun `dayPrograms devuelve el rango del día ordenado`() = runTest {
        val start = now - 12 * h
        val end = now + 12 * h
        db.epgDao().insertBatch(
            listOf(
                EpgProgramEntity(tvgId = "c1.ext", startMillis = now + 2 * h, endMillis = now + 3 * h, title = "C"),
                EpgProgramEntity(tvgId = "c1.ext", startMillis = now - h, endMillis = now + h, title = "B"),
                EpgProgramEntity(tvgId = "c1.ext", startMillis = now + 30 * h, endMillis = now + 31 * h, title = "Fuera"),
                EpgProgramEntity(tvgId = "c2", startMillis = now, endMillis = now + h, title = "Otro canal"),
            ),
        )
        val day = matcher.dayPrograms("c1.ext", start, end)
        assertEquals(listOf("B", "C"), day.map { it.title })
    }

    @Test
    fun `nowNext por lote devuelve ahora y siguiente por canal`() = runTest {
        db.epgDao().insertBatch(
            listOf(
                EpgProgramEntity(tvgId = "c1.ext", startMillis = now - h, endMillis = now + h, title = "Ahora"),
                EpgProgramEntity(tvgId = "c1.ext", startMillis = now + h, endMillis = now + 2 * h, title = "Luego"),
            ),
        )
        val nn = db.epgDao().nowNext(listOf("c1.ext"), now)
        assertEquals("Ahora", nn["c1.ext"]?.first?.title)
        assertEquals("Luego", nn["c1.ext"]?.second?.title)
    }
}
