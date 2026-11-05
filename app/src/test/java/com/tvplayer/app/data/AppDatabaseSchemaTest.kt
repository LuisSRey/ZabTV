package com.tvplayer.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.data.db.AppDatabase
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.EpgProgramEntity
import com.tvplayer.app.data.db.FavouriteEntity
import com.tvplayer.app.data.db.ListEntity
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
    fun listarListasYCanalConUplift() {
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
    fun favoritoEsIdempotentePorClaveCompuesta() {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "http://x.m3u8"))
        assertTrue(db.favouriteDao().toggle(listId, "c1.ext"))
        assertTrue(db.favouriteDao().toggle(listId, "c1.ext")) // togglear dos veces == off?? NO: segunda vez false = apagado
        assertFalse(db.favouriteDao().exists(listId, "c1.ext"))
        assertTrue(db.favouriteDao().toggle(listId, "c1.ext"))
        assertTrue(db.favouriteDao().exists(listId, "c1.ext"))
    }

    @Test
    fun favoritosDeListasDistintasNoColisionan() {
        val a = db.listDao().upsert(ListEntity(name = "A", sourceUrl = "http://a.m3u8"))
        val b = db.listDao().upsert(ListEntity(name = "B", sourceUrl = "http://b.m3u8"))
        db.favouriteDao().toggle(a, "same")
        assertEquals(1, db.favouriteDao().favouritesListId(a).size)
        assertEquals(0, db.favouriteDao().favouritesListId(b).size)
    }

    @Test
    fun epgVentanaPorCanalYTiempo() {
        val now = 1_770_000_000_000L
        db.epgDao().insertBatch(
            listOf(
                EpgProgramEntity(tvgId = "c1.ext", startMillis = now, endMillis = now + 3_600_000, title = "Ahora"),
                EpgProgramEntity(tvgId = "c1.ext", startMillis = now + 3_600_000, endMillis = now + 7_200_000, title = "Luego"),
                EpgProgramEntity(tvgId = "c9", startMillis = now, endMillis = now + 600_000, title = "Otro canal"),
            ),
        )
        val nn = db.epgDao().nowNext(listOf("c1.ext"), now)
        assertEquals("Ahora", nn["c1.ext"]?.first?.title)
        assertEquals("Luego", nn["c1.ext"]?.second?.title)
        assertEquals(1, db.epgDao().dayPrograms("c1.ext", now, now + 7_200_000).size)
        assertEquals(3, db.epgDao().count())
        db.epgDao().pruneBefore(now + 900_000)
        assertEquals(1, db.epgDao().count())
    }

    @Test
    fun settingsKv() {
        db.settingsDao().put("allow_http", "true")
        assertEquals("true", db.settingsDao().get("allow_http", null))
        assertEquals("default", db.settingsDao().get("no_existe", default = "default"))
    }
}
