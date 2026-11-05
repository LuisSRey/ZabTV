package com.tvplayer.app.ui.channels

import android.content.Context
import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.data.db.AppDatabase
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.ListEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var vm: ChannelBrowserViewModel

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
        val rows = mutableListOf<ChannelEntity>()
        repeat(2) {
            rows += ChannelEntity(
                listId = listId, displayName = "A$it", normName = "a $it",
                groupName = "A", url = "http://s/a$it", stableKey = "ka$it", position = it,
            )
        }
        repeat(3) {
            rows += ChannelEntity(
                listId = listId, displayName = "B$it", normName = "b $it",
                groupName = "B", url = "http://s/b$it", stableKey = "kb$it", position = 10 + it,
            )
        }
        rows += ChannelEntity(
            listId = listId, displayName = "C1", normName = "c 1",
            groupName = "C", url = "http://s/c1", stableKey = "kc1", position = 20,
        )
        db.channelDao().upsertAll(rows)
        return listId
    }

    private suspend fun asSnapshotNames(group: String?): List<String> {
        vm.select(group)
        return vm.channels.asSnapshot().map { it.displayName }
    }

    @Test
    fun `groups calcula conteos por grupo`() = runTest {
        val listId = seed()
        vm = ChannelBrowserViewModel(db.channelDao(), listId)
        val groups = vm.groups()
        assertEquals(listOf("A" to 2, "B" to 3, "C" to 1), groups.map { it.group to it.count })
    }

    @Test
    fun `todos muestra los 6 en orden de posicion`() = runTest {
        val listId = seed()
        vm = ChannelBrowserViewModel(db.channelDao(), listId)
        val names = asSnapshotNames(null)
        assertEquals(6, names.size)
        assertEquals(listOf("A0", "A1", "B0", "B1", "B2", "C1"), names)
    }

    @Test
    fun `cambiar de grupo cambia la fuente de paginado`() = runTest {
        val listId = seed()
        vm = ChannelBrowserViewModel(db.channelDao(), listId)
        assertEquals(listOf("B0", "B1", "B2"), asSnapshotNames("B"))
        assertEquals(listOf("C1"), asSnapshotNames("C"))
    }

    /**
     * Patrón exacto del fragment: colección continua + submitData al adapter.
     * Al cambiar de grupo el adapter debe mostrar solo ese grupo (bug device).
     * Las cargas de Room/Paging corren en hilos reales → espera en tiempo real.
     */
    @Test
    fun `cambiar de grupo actualiza el adapter en coleccion continua`() = runTest {
        val listId = seed()
        vm = ChannelBrowserViewModel(db.channelDao(), listId)
        val adapter = ChannelGridAdapter(onClick = {})
        val job = launch { vm.channels.collectLatest { adapter.submitData(it) } }
        withContext(Dispatchers.IO) {
            withTimeout(15_000) {
                adapter.loadStateFlow.first { it.refresh is androidx.paging.LoadState.NotLoading }
            }
        }
        assertEquals(6, adapter.itemCount)
        vm.select("B")
        withContext(Dispatchers.IO) {
            withTimeout(15_000) {
                adapter.loadStateFlow.first {
                    it.refresh is androidx.paging.LoadState.NotLoading && adapter.itemCount == 3
                }
            }
        }
        assertEquals(3, adapter.itemCount)
        assertEquals(
            listOf("B0", "B1", "B2"),
            (0 until adapter.itemCount).map { adapter.peek(it)!!.displayName },
        )
        job.cancel()
    }
}
