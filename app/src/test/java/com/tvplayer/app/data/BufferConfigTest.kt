package com.tvplayer.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.data.db.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Buffer configurable del reproductor (spec §6: back 8 s / min 15 s / max 30 s).
 * Sin valores guardados → defaults del spec; valores absurdos → clamp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BufferConfigTest {

    private lateinit var db: AppDatabase
    private lateinit var container: AppContainer

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
    fun `sin ajustes devuelve los defaults del spec`() = runTest {
        assertEquals(BufferConfig(), BufferConfig.load(db))
        assertEquals(15_000, BufferConfig().minMs)
        assertEquals(30_000, BufferConfig().maxMs)
        assertEquals(8_000, BufferConfig().backMs)
    }

    @Test
    fun `save y load roundtrip`() = runTest {
        BufferConfig.save(db, BufferConfig(minMs = 10_000, maxMs = 20_000, backMs = 5_000))
        assertEquals(BufferConfig(minMs = 10_000, maxMs = 20_000, backMs = 5_000), BufferConfig.load(db))
    }

    @Test
    fun `valores absurdos se clamp al leer`() = runTest {
        BufferConfig.save(db, BufferConfig(minMs = -5, maxMs = 1_000, backMs = -1))
        val loaded = BufferConfig.load(db)
        // max < min y negativos → saneado a algo reproducible y seguro
        assertEquals(BufferConfig(minMs = 1_000, maxMs = 1_000, backMs = 0), loaded)
    }
}
