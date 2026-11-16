package com.zabtv.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.data.db.AppDatabase
import com.zabtv.app.data.db.EpgProgramEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guía S1: recent_watches (orden + cap 20) + programsInRange (solape +
 * orden). TDD RED (el código existe en el árbol pero sin cobertura).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentDaoTest {

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
    fun `recordWatch ordena por mas reciente`() = runTest {
        db.recentDao().recordWatch(1, "a", 1000)
        db.recentDao().recordWatch(1, "b", 2000)
        val recent = db.recentDao().recentForList(1)
        assertEquals(listOf("b", "a"), recent.map { it.stableKey })
    }

    @Test
    fun `re-ver sube al frente`() = runTest {
        db.recentDao().recordWatch(1, "a", 1000)
        db.recentDao().recordWatch(1, "b", 2000)
        db.recentDao().recordWatch(1, "a", 3000)
        assertEquals("a", db.recentDao().recentForList(1).first().stableKey)
    }

    @Test
    fun `cap 20 poda al registrar`() = runTest {
        repeat(25) { db.recentDao().recordWatch(1, "k$it", 1000L + it) }
        assertEquals(20, db.recentDao().countForList(1))
        val keys = db.recentDao().recentForList(1).map { it.stableKey }
        assertTrue(keys.contains("k24"))
        assertTrue(keys.none { it == "k0" })
    }

    @Test
    fun `programsInRange solo solape y ordenado`() = runTest {
        db.epgDao().insertBatch(
            listOf(
                EpgProgramEntity(tvgId = "c1", startMillis = 1000, endMillis = 2000, title = "Dentro"),
                EpgProgramEntity(tvgId = "c1", startMillis = 0, endMillis = 500, title = "Antes"),
                EpgProgramEntity(tvgId = "c1", startMillis = 3000, endMillis = 4000, title = "Despues"),
                EpgProgramEntity(tvgId = "c2", startMillis = 1200, endMillis = 1800, title = "Otro"),
            ),
        )
        val rows = db.epgDao().programsInRange(listOf("c1", "c2"), 1000, 2500)
        assertEquals(listOf("Dentro", "Otro"), rows.map { it.title })
    }
}
