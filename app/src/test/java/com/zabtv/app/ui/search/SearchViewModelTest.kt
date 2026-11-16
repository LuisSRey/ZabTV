package com.zabtv.app.ui.search

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.data.db.AppDatabase
import com.zabtv.app.data.db.ChannelEntity
import com.zabtv.app.data.db.ListEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 10: búsqueda accent-insensible sobre normName (el DAO ya la cubre;
 * aquí el contrato del ViewModel: query normalizada, vacío → lista vacía,
 * sin resultados → lista vacía).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var vm: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun seed(): Long {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "http://x/lista.m3u"))
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(
                    listId = listId, displayName = "Canal Español HD", normName = "canal espanol",
                    groupName = "G", url = "http://s/a.m3u8", stableKey = "ka", position = 0,
                ),
                ChannelEntity(
                    listId = listId, displayName = "Otro", normName = "otro",
                    groupName = "G", url = "http://s/b.m3u8", stableKey = "kb", position = 1,
                ),
            ),
        )
        return listId
    }

    @Test
    fun `query con acentos encuentra por normName`() = runTest {
        val listId = seed()
        vm = SearchViewModel(db.channelDao(), listId)
        val hits = vm.search("español")
        assertEquals(1, hits.size)
        assertEquals("http://s/a.m3u8", hits.first().url)
    }

    @Test
    fun `query vacía devuelve lista vacía sin tocar DB`() = runTest {
        val listId = seed()
        vm = SearchViewModel(db.channelDao(), listId)
        assertTrue(vm.search("").isEmpty())
        assertTrue(vm.search("   ").isEmpty())
    }

    @Test
    fun `sin coincidencias devuelve lista vacía`() = runTest {
        val listId = seed()
        vm = SearchViewModel(db.channelDao(), listId)
        assertTrue(vm.search("noexiste").isEmpty())
    }
}
