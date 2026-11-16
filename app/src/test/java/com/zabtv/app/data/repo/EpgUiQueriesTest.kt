package com.zabtv.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.data.db.AppDatabase
import com.zabtv.app.data.db.ChannelEntity
import com.zabtv.app.data.db.EpgChannelEntity
import com.zabtv.app.data.db.EpgProgramEntity
import com.zabtv.app.data.db.ListEntity
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
    fun `fallback tolera prefijos tipo proveedor`() = runTest {
        db.epgDao().insertChannels(
            listOf(EpgChannelEntity(tvgId = "xml.9", displayName = "ES | Telecinco HD")),
        )
        db.epgDao().insertBatch(
            listOf(EpgProgramEntity(tvgId = "xml.9", startMillis = now - h, endMillis = now + h, title = "Ahora")),
        )
        val match = matcher.match(channel(null, "Telecinco"))
        assertEquals("xml.9", match!!.tvgId)
        assertTrue(match.approximate)
    }

    @Test
    fun `nombre corto generico no hace match por subcadena`() = runTest {
        db.epgDao().insertChannels(
            listOf(EpgChannelEntity(tvgId = "xml.2", displayName = "Canal 2")),
        )
        db.epgDao().insertBatch(
            listOf(EpgProgramEntity(tvgId = "xml.2", startMillis = now - h, endMillis = now + h, title = "Ahora")),
        )
        // "Canal" solo no basta para colgarse de "Canal 2": evita falsos positivos.
        assertNull(matcher.match(channel(null, "Canal")))
    }

    @Test
    fun `sin programas en ningún lado devuelve null`() = runTest {
        assertNull(matcher.match(channel("c9", "Nadie")))
        assertNull(matcher.match(channel(null, "Tampoco")))
    }

    @Test
    fun `fallback calza tvg-ids estilo Elcano con EPG dobleM`() = runTest {
        // Regresión lista Elcano: tvg-id "DAZN_1_1080p_" vs EPG "DAZN 1 HD".
        db.epgDao().insertChannels(
            listOf(
                EpgChannelEntity(tvgId = "DAZN 1 HD", displayName = "DAZN 1 HD"),
                EpgChannelEntity(tvgId = "M+ LaLiga HD", displayName = "M+ LaLiga HD"),
                EpgChannelEntity(tvgId = "DAZN Baloncesto", displayName = "DAZN Baloncesto"),
            ),
        )
        db.epgDao().insertBatch(
            listOf(
                EpgProgramEntity(tvgId = "DAZN 1 HD", startMillis = now - h, endMillis = now + h, title = "Ahora"),
                EpgProgramEntity(tvgId = "M+ LaLiga HD", startMillis = now - h, endMillis = now + h, title = "Ahora"),
                EpgProgramEntity(tvgId = "DAZN Baloncesto", startMillis = now - h, endMillis = now + h, title = "Ahora"),
            ),
        )
        val m1 = matcher.match(channel("DAZN_1_1080p_", "DAZN 1 1080p **"))
        assertEquals("DAZN 1 HD", m1!!.tvgId)
        assertTrue(m1.approximate)
        val m2 = matcher.match(channel("M_LaLiga_720p_", "M+ LaLiga 720p *"))
        assertEquals("M+ LaLiga HD", m2!!.tvgId)
        assertTrue(m2.approximate)
        val m3 = matcher.match(channel("DAZN_Baloncesto_720p_", "DAZN Baloncesto 720p *"))
        assertEquals("DAZN Baloncesto", m3!!.tvgId)
        assertTrue(m3.approximate)
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
