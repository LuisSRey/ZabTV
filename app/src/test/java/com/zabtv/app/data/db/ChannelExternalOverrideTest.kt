package com.zabtv.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
 * S4: override por canal (allowExternal Boolean? + MIGRATION_2_3).
 * null = Global, true = Siempre, false = Nunca.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelExternalOverrideTest {

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
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "https://x/lista.m3u"))
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(
                    listId = listId, displayName = "A", normName = "a", groupName = "G",
                    url = "https://s/a.m3u8", stableKey = "k-a", position = 0,
                ),
            ),
        )
        return listId
    }

    @Test
    fun `default es Global (null)`() = runTest {
        val listId = seed()
        assertNull(db.channelDao().channelByKey(listId, "k-a")!!.allowExternal)
    }

    @Test
    fun `override Siempre y Nunca persisten`() = runTest {
        val listId = seed()
        db.channelDao().setAllowExternal(listId, "k-a", true)
        assertEquals(true, db.channelDao().channelByKey(listId, "k-a")!!.allowExternal)
        db.channelDao().setAllowExternal(listId, "k-a", false)
        assertEquals(false, db.channelDao().channelByKey(listId, "k-a")!!.allowExternal)
    }

    @Test
    fun `volver a Global borra el override`() = runTest {
        val listId = seed()
        db.channelDao().setAllowExternal(listId, "k-a", true)
        db.channelDao().setAllowExternal(listId, "k-a", null)
        assertNull(db.channelDao().channelByKey(listId, "k-a")!!.allowExternal)
    }
}
