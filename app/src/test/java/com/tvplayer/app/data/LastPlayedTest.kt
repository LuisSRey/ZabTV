package com.tvplayer.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.data.db.AppDatabase
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.ListEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Último canal reproducido: se guarda por stableKey (sobrevive reimports con
 * nuevo orden) y se resuelve a la posición actual.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LastPlayedTest {

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

    private suspend fun seed(): Long {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "http://x/lista.m3u"))
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(
                    listId = listId, displayName = "A", normName = "a", groupName = "G",
                    url = "http://s/a.m3u8", stableKey = "ka", position = 0,
                ),
                ChannelEntity(
                    listId = listId, displayName = "B", normName = "b", groupName = "G",
                    url = "http://s/b.m3u8", stableKey = "kb", position = 1,
                ),
            ),
        )
        return listId
    }

    @Test
    fun `sin reproduccion devuelve null`() = runTest {
        seed()
        assertNull(LastPlayed.resolve(db))
    }

    @Test
    fun `guardar y resolver devuelve el canal con su posicion`() = runTest {
        val listId = seed()
        LastPlayed.save(db, listId, "kb")
        val channel = LastPlayed.resolve(db)
        assertEquals("B", channel!!.displayName)
        assertEquals(1, channel.position)
    }

    @Test
    fun `sobrevive a reimport con orden distinto`() = runTest {
        val listId = seed()
        LastPlayed.save(db, listId, "kb")
        // Reimport: B ahora es posición 0
        db.channelDao().deleteForList(listId)
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(
                    listId = listId, displayName = "B", normName = "b", groupName = "G",
                    url = "http://s/b2.m3u8", stableKey = "kb", position = 0,
                ),
            ),
        )
        val channel = LastPlayed.resolve(db)
        assertEquals(0, channel!!.position)
        assertEquals("http://s/b2.m3u8", channel.url)
    }

    @Test
    fun `si el canal desaparece devuelve null`() = runTest {
        val listId = seed()
        LastPlayed.save(db, listId, "kb")
        db.channelDao().deleteForList(listId)
        assertNull(LastPlayed.resolve(db))
    }
}
