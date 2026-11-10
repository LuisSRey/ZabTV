package com.tvplayer.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.data.db.AppDatabase
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.EpgProgramEntity
import com.tvplayer.app.data.db.ListEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseSchemaTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun listaCanalCount() = runTest {
        val listId = db.listDao().upsert(ListEntity(name = "Home", sourceUrl = "http://example.com/lista.m3u"))
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(
                    listId = listId,
                    tvgId = "c1.ext",
                    displayName = "Canal Uno",
                    normName = "canal uno",
                    groupName = "General",
                    logoUrl = "http://example.com/c1.png",
                    url = "http://example.com/c1.m3u8",
                    stableKey = "c1.ext",
                    position = 0,
                ),
            ),
        )
        assertEquals(1, db.channelDao().countForList(listId))
        assertEquals(1, db.listDao().all().size)
    }

    @Test
    fun favoritoTogglePorClaveCompuesta() = runTest {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "http://x.m3u8"))
        assertTrue(db.favouriteDao().toggle(listId, "c1.ext")) // on
        assertTrue(db.favouriteDao().exists(listId, "c1.ext"))
        assertFalse(db.favouriteDao().toggle(listId, "c1.ext")) // off
        assertFalse(db.favouriteDao().exists(listId, "c1.ext"))
        assertTrue(db.favouriteDao().toggle(listId, "c1.ext")) // on de nuevo
        assertTrue(db.favouriteDao().exists(listId, "c1.ext"))
    }

    @Test
    fun favoritosDeListasDistintasNoColisionan() = runTest {
        val a = db.listDao().upsert(ListEntity(name = "A", sourceUrl = "http://a.m3u8"))
        val b = db.listDao().upsert(ListEntity(name = "B", sourceUrl = "http://b.m3u8"))
        // El rail hace JOIN con channels: sin fila de canal no hay resultado
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(listId = a, displayName = "A1", normName = "a1", groupName = "G", url = "http://s/a1", stableKey = "same", position = 0),
            ),
        )
        db.favouriteDao().toggle(a, "same")
        db.favouriteDao().toggle(b, "same")
        assertEquals(1, db.favouriteDao().favouritesListId(a).size)
        assertEquals(0, db.favouriteDao().favouritesListId(b).size) // su canal no existe en channels
    }

    @Test
    fun favoritoSobreviveReimportConMismaClaveYUrlDistinta() = runTest {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "http://x.m3u8"))
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(
                    listId = listId, displayName = "Canal Uno", normName = "canal uno",
                    groupName = "General", url = "http://old.example.com/a.m3u8", stableKey = "c1.ext", position = 0,
                ),
            ),
        )
        db.favouriteDao().toggle(listId, "c1.ext")
        // Reimport: misma stableKey, url nueva → el favorito debe seguir vivo (spec F4)
        db.channelDao().deleteForList(listId)
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(
                    listId = listId, displayName = "Canal Uno", normName = "canal uno",
                    groupName = "General", url = "http://new.example.com/a.m3u8", stableKey = "c1.ext", position = 0,
                ),
            ),
        )
        val favs = db.favouriteDao().favouritesListId(listId)
        assertEquals(1, favs.size)
        assertEquals("http://new.example.com/a.m3u8", favs.first().url)
    }

    @Test
    fun upsertReemplazaPorIndiceUnicoListIdStableKey() = runTest {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "http://x.m3u8"))
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(listId = listId, displayName = "A", normName = "a", groupName = "G", url = "http://s/a", stableKey = "k-a", position = 0),
                ChannelEntity(listId = listId, displayName = "A2", normName = "a2", groupName = "G", url = "http://s/a2", stableKey = "k-a2", position = 1),
            ),
        )
        // Reimport: mismo stableKey con url nueva → SQLite REPLACE sustituye la fila conflictiva (índice única)
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(listId = listId, displayName = "A nueva", normName = "a", groupName = "G", url = "http://s/a-nueva", stableKey = "k-a", position = 0),
            ),
        )
        val all = db.channelDao().allForListAndGroup(listId, null)
        assertEquals(2, all.size)
        assertEquals("A nueva", all.first { it.stableKey == "k-a" }.displayName)
        assertEquals("http://s/a-nueva", all.first { it.stableKey == "k-a" }.url)
    }

    @Test
    fun gruposYEindex() = runTest {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "http://x.m3u8"))
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(listId = listId, displayName = "A", normName = "a", groupName = "Deportes", url = "http://s/a", stableKey = "k1", position = 0),
                ChannelEntity(listId = listId, displayName = "B", normName = "b", groupName = "Noticias", url = "http://s/b", stableKey = "k2", position = 1),
                ChannelEntity(listId = listId, displayName = "C", normName = "c", groupName = "Deportes", url = "http://s/c", stableKey = "k3", position = 2),
            ),
        )
        val groups = db.channelDao().groupsForList(listId)
        assertEquals(2, groups.size)
        assertEquals("Deportes", groups.first().group)
        assertEquals(2, groups.first().count)
        assertEquals(2, db.channelDao().allForListAndGroup(listId, "Deportes").size)
    }

    @Test
    fun busquedaPorNormNameLikeAccentosInsensibles() = runTest {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "http://x.m3u8"))
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(listId = listId, displayName = "Canal Spanish™ HD", normName = "canal spanish", groupName = "G", url = "http://s/s", stableKey = "ka", position = 0),
                ChannelEntity(listId = listId, displayName = "Otro", normName = "otro", groupName = "G", url = "http://s/o", stableKey = "kb", position = 1),
            ),
        )
        val hits = db.channelDao().searchByNorm(listId, "spanish")
        assertEquals(1, hits.size)
        assertEquals("http://s/s", hits.first().url)
        assertTrue(db.channelDao().searchByNorm(listId, "noexiste").isEmpty())
    }

    @Test
    fun epgVentanaPorCanalYTiempo() = runTest {
        val now = 1_770_000_000_000L
        db.epgDao().insertBatch(
            listOf(
                EpgProgramEntity(tvgId = "c1.ext", startMillis = now, endMillis = now + 3_600_000, title = "Ahora"),
                EpgProgramEntity(tvgId = "c1.ext", startMillis = now + 3_600_000, endMillis = now + 7_200_000, title = "Luego"),
                EpgProgramEntity(tvgId = "c1.ext", startMillis = now + 7_200_000 + 600_000, endMillis = now + 10_000_000, title = "Después"),
                EpgProgramEntity(tvgId = "c9", startMillis = now, endMillis = now + 600_000, title = "Otro canal"),
            ),
        )
        val nn = db.epgDao().nowNext(listOf("c1.ext"), now)
        assertEquals("Ahora", nn["c1.ext"]?.first?.title)
        assertEquals("Luego", nn["c1.ext"]?.second?.title)
        val day = db.epgDao().dayPrograms("c1.ext", now, now + 10_000_000)
        assertEquals(3, day.size)
        assertEquals(4, db.epgDao().count())
        db.epgDao().pruneBefore(now + 900_000)
        // Verificación exacta: pruneBefore borra lo que TERMINA antes del corte;
        // c9 (end=+10min<+15min) también se va → quedan Ahora/Luego/Después
        assertEquals(3, db.epgDao().count())
        val restantes = db.epgDao().dayPrograms("c1.ext", 0, Long.MAX_VALUE)
        assertEquals(listOf("Ahora", "Luego", "Después"), restantes.map { it.title })
    }

    @Test
    fun settingsKv() = runTest {
        db.settingsDao().put("allow_http", "true")
        assertEquals("true", db.settingsDao().get("allow_http", null))
        assertEquals("default", db.settingsDao().get("no_existe", default = "default"))
    }

    @Test
    fun borrarListaLimpiaSoloSusFavoritos() = runTest {
        val a = db.listDao().upsert(ListEntity(name = "A", sourceUrl = "http://a.m3u8"))
        val b = db.listDao().upsert(ListEntity(name = "B", sourceUrl = "http://b.m3u8"))
        db.favouriteDao().toggle(a, "k1")
        db.favouriteDao().toggle(b, "k2")
        db.favouriteDao().deleteForList(a)
        assertFalse(db.favouriteDao().exists(a, "k1"))
        assertTrue(db.favouriteDao().exists(b, "k2"))
    }
}
