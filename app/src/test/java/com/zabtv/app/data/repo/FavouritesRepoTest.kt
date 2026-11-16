package com.zabtv.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.data.db.AppDatabase
import com.zabtv.app.data.db.ChannelEntity
import com.zabtv.app.data.db.ListEntity
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

/**
 * Task 9: el favorito se ancla a la StableKey (tvg-id o sha1 de norm+grupo),
 * nunca a la URL ni al rowId — sobrevive a reimportaciones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavouritesRepoTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: FavouritesRepo

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = FavouritesRepo(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(): Pair<Long, ChannelEntity> {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "http://x/lista.m3u"))
        val channel = ChannelEntity(
            listId = listId, tvgId = "c1.ext", displayName = "Canal Uno",
            normName = "canal uno", groupName = "General",
            url = "http://old.example.com/a.m3u8", stableKey = "c1.ext", position = 0,
        )
        db.channelDao().upsertAll(listOf(channel))
        return listId to channel
    }

    @Test
    fun `toggle usa la stableKey almacenada aunque colisione el nombre`() = runTest {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "http://x/lista.m3u"))
        // Canal duplicado: mismo nombre/grupo que otro, clave con sufijo (la genera ImportService).
        val dupe = ChannelEntity(
            listId = listId, tvgId = "dup.ext", displayName = "Dupe", normName = "dupe",
            groupName = "G", url = "http://s/dup2.m3u8", stableKey = "dup.ext#2", position = 101,
        )
        db.channelDao().upsertAll(listOf(dupe))
        assertTrue(repo.toggle(listId, dupe))
        assertTrue(db.favouriteDao().exists(listId, "dup.ext#2"))
        assertFalse(db.favouriteDao().exists(listId, "dup.ext"))
    }

    @Test
    fun `toggle marca y desmarca devolviendo el estado final`() = runTest {
        val (listId, channel) = seed()
        assertTrue(repo.toggle(listId, channel)) // on
        assertTrue(repo.isFavourite(listId, channel))
        assertFalse(repo.toggle(listId, channel)) // off
        assertFalse(repo.isFavourite(listId, channel))
    }

    @Test
    fun `favorito sobrevive a reimport con misma clave y URL distinta`() = runTest {
        val (listId, channel) = seed()
        repo.toggle(listId, channel)
        // Reimport: misma stableKey, URL nueva
        db.channelDao().deleteForList(listId)
        db.channelDao().upsertAll(
            listOf(channel.copy(url = "http://new.example.com/a.m3u8")),
        )
        val favs = repo.favourites(listId)
        assertEquals(1, favs.size)
        assertEquals("http://new.example.com/a.m3u8", favs.first().url)
    }

    @Test
    fun `sin tvg-id sobrevive por hash de nombre y grupo`() = runTest {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "http://x/lista.m3u"))
        val channel = ChannelEntity(
            listId = listId, displayName = "Canal Dos HD", normName = "canal dos",
            groupName = "General", url = "http://s/b.m3u8",
            stableKey = com.zabtv.app.core.StableKey.of(null, "Canal Dos HD", "General"),
            position = 0,
        )
        db.channelDao().upsertAll(listOf(channel))
        assertTrue(repo.toggle(listId, channel))
        // Reimport con nombre equivalente (mismo hash) y URL distinta
        db.channelDao().deleteForList(listId)
        db.channelDao().upsertAll(
            listOf(channel.copy(displayName = "Canal Dos", url = "http://s/b2.m3u8")),
        )
        val favs = repo.favourites(listId)
        assertEquals(1, favs.size)
        assertEquals("http://s/b2.m3u8", favs.first().url)
    }
}
