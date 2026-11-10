package com.tvplayer.app.ui.channels

import android.content.Context
import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.data.db.AppDatabase
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.EpgProgramEntity
import com.tvplayer.app.data.db.ListEntity
import com.tvplayer.app.ui.channels.ChannelSidebar.Selection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fusión Guía→Canales (S1): ChannelBrowserViewModel ampliado — paginado
 * All/Group vía pagingForListAndGroup, Historial vía PagingData.from (cap 20),
 * hero (último visto o primero, con ahora/siguiente/progreso), ventana del
 * timeline 3 h y reparto por canal. TDD RED.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var vm: ChannelBrowserViewModel
    private val now = 1_794_296_400_000L // 2026-11-10T19:40:00Z

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

    private fun newVm(listId: Long) =
        ChannelBrowserViewModel(listId, db.channelDao(), db.epgDao(), db.recentDao(), nowProvider = { now })

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

    private suspend fun seedGuide(): Long {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "http://x/lista.m3u"))
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(listId = listId, tvgId = "c1", displayName = "Uno", normName = "uno", groupName = "G", url = "http://s/1", stableKey = "c1", position = 0),
                ChannelEntity(listId = listId, tvgId = "c2", displayName = "Dos", normName = "dos", groupName = "G", url = "http://s/2", stableKey = "c2", position = 1),
            ),
        )
        return listId
    }

    private suspend fun asSnapshotNames(sel: Selection): List<String> {
        vm.select(sel)
        return vm.channels.asSnapshot().map { it.displayName }
    }

    /**
     * La rama Historial emite PagingData.from (snapshot, no Pager): la tira se
     * verifica con el patrón del fragment (colección continua + submitData +
     * peek) esperando por itemCount — loadStateFlow no emite NotLoading para
     * PagingData.from bajo Robolectric/runTest aunque la presentación sí
     * ocurre (verificado por experimento). PagingData.from + submitData ya
     * funciona en producción (Buscar/Favoritos en el dispositivo).
     */
    private suspend fun TestScope.collectNames(sel: Selection, expectedCount: Int): List<String> {
        // Selección ANTES de coleccionar: la primera emisión ya es el filtro
        // pedido (sin carrera con el All inicial).
        vm.select(sel)
        val adapter = ChannelGridAdapter(onClick = {})
        val job = launch { vm.channels.collectLatest { adapter.submitData(it) } }
        withContext(Dispatchers.IO) {
            withTimeout(15_000) {
                while (adapter.itemCount != expectedCount) kotlinx.coroutines.delay(50)
            }
        }
        val names = (0 until adapter.itemCount).map { adapter.peek(it)!!.displayName }
        job.cancel()
        return names
    }

    @Test
    fun `groups calcula conteos por grupo`() = runTest {
        val listId = seed()
        vm = newVm(listId)
        val groups = vm.groups()
        assertEquals(listOf("A" to 2, "B" to 3, "C" to 1), groups.map { it.group to it.count })
    }

    @Test
    fun `todos muestra los 6 en orden de posicion`() = runTest {
        val listId = seed()
        vm = newVm(listId)
        val names = asSnapshotNames(Selection.All)
        assertEquals(6, names.size)
        assertEquals(listOf("A0", "A1", "B0", "B1", "B2", "C1"), names)
    }

    @Test
    fun `cambiar de grupo cambia la fuente de paginado`() = runTest {
        val listId = seed()
        vm = newVm(listId)
        assertEquals(listOf("B0", "B1", "B2"), asSnapshotNames(Selection.Group("B")))
        assertEquals(listOf("C1"), asSnapshotNames(Selection.Group("C")))
    }

    /**
     * Patrón exacto del fragment: colección continua + submitData al adapter.
     * Al cambiar de grupo el adapter debe mostrar solo ese grupo (bug device).
     * Las cargas de Room/Paging corren en hilos reales → espera en tiempo real.
     */
    @Test
    fun `cambiar de grupo actualiza el adapter en coleccion continua`() = runTest {
        val listId = seed()
        vm = newVm(listId)
        val adapter = ChannelGridAdapter(onClick = {})
        val job = launch { vm.channels.collectLatest { adapter.submitData(it) } }
        withContext(Dispatchers.IO) {
            withTimeout(15_000) {
                adapter.loadStateFlow.first { it.refresh is androidx.paging.LoadState.NotLoading }
            }
        }
        assertEquals(6, adapter.itemCount)
        vm.select(Selection.Group("B"))
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

    @Test
    fun `historial pagina lo visto en orden con cap 20`() = runTest {
        val listId = seedGuide()
        vm = newVm(listId)
        db.recentDao().recordWatch(listId, "c1", now - 2_000)
        db.recentDao().recordWatch(listId, "c2", now - 1_000)
        assertEquals(listOf("Dos", "Uno"), collectNames(Selection.History, 2))
    }

    @Test
    fun `historial ignora claves borradas y capa en 20`() = runTest {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "http://x/lista.m3u"))
        val rows = (0 until 25).map {
            ChannelEntity(listId = listId, displayName = "C$it", normName = "c $it", groupName = "G", url = "http://s/$it", stableKey = "k$it", position = it)
        }
        db.channelDao().upsertAll(rows)
        vm = newVm(listId)
        repeat(25) { db.recentDao().recordWatch(listId, "k$it", now - (25 - it)) }
        val names = collectNames(Selection.History, 20)
        assertEquals(20, names.size)
        // Recientes primero y los 5 más viejos fuera del cap.
        assertEquals("C24", names.first())
        assertTrue(names.none { it == "C0" })
    }

    @Test
    fun `setListId cambia de lista en el flow`() = runTest {
        val a = seed()
        val b = db.listDao().upsert(ListEntity(name = "M", sourceUrl = "http://x/m.m3u"))
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(listId = b, displayName = "Solo", normName = "solo", groupName = "G", url = "http://s/z", stableKey = "kz", position = 0),
            ),
        )
        vm = newVm(a)
        assertEquals(6, asSnapshotNames(Selection.All).size)
        vm.setListId(b)
        assertEquals(listOf("Solo"), asSnapshotNames(Selection.All))
    }

    @Test
    fun `hero sin historial es el primer canal`() = runTest {
        val listId = seedGuide()
        vm = newVm(listId)
        assertEquals("Uno", vm.hero()?.channel?.displayName)
    }

    @Test
    fun `hero es el ultimo visto aunque cambie el orden`() = runTest {
        val listId = seedGuide()
        db.recentDao().recordWatch(listId, "c2", now - 1_000)
        vm = newVm(listId)
        assertEquals("Dos", vm.hero()?.channel?.displayName)
    }

    @Test
    fun `hero trae el ahora de la EPG`() = runTest {
        val listId = seedGuide()
        db.epgDao().insertBatch(
            listOf(
                EpgProgramEntity(tvgId = "c1", startMillis = now - 600_000, endMillis = now + 600_000, title = "Telediario"),
            ),
        )
        vm = newVm(listId)
        assertEquals("Telediario", vm.hero()?.nowTitle)
    }

    @Test
    fun `timeline reparte por canal dentro de la ventana 3h`() = runTest {
        val listId = seedGuide()
        vm = newVm(listId)
        val window = vm.window()
        assertEquals(6, window.slotCount)
        assertEquals(30, window.slotMinutes)
        db.epgDao().insertBatch(
            listOf(
                EpgProgramEntity(tvgId = "c1", startMillis = window.startMillis + 60_000, endMillis = window.startMillis + 600_000, title = "Dentro"),
                EpgProgramEntity(tvgId = "c1", startMillis = window.endMillis + 3_600_000, endMillis = window.endMillis + 7_200_000, title = "Fuera"),
                EpgProgramEntity(tvgId = "c2", startMillis = window.startMillis + 60_000, endMillis = window.startMillis + 600_000, title = "Otro"),
            ),
        )
        val tl = vm.timeline(listOf("c1", "c2"), window)
        assertEquals(listOf("Dentro"), tl["c1"]?.map { it.title })
        assertEquals(listOf("Otro"), tl["c2"]?.map { it.title })
    }

    @Test
    fun `canales por grupo en orden`() = runTest {
        val listId = seedGuide()
        vm = newVm(listId)
        val all = vm.channelsForGroup(null)
        assertEquals(listOf("Uno", "Dos"), all.map { it.displayName })
        assertNotNull(vm.groups())
        assertTrue(vm.groups().isNotEmpty())
    }

    @Test
    fun `hero trae progreso del ahora y siguiente`() = runTest {
        val listId = seedGuide()
        db.epgDao().insertBatch(
            listOf(
                EpgProgramEntity(tvgId = "c1", startMillis = now - 600_000, endMillis = now + 600_000, title = "Ahora"),
                EpgProgramEntity(tvgId = "c1", startMillis = now + 600_000, endMillis = now + 1_800_000, title = "Despues"),
            ),
        )
        vm = newVm(listId)
        val hero = vm.hero()
        assertEquals("Ahora", hero?.nowTitle)
        assertEquals("Despues", hero?.nextTitle)
        assertEquals(0.5f, hero?.progress ?: -1f)
    }

    @Test
    fun `hero sin guia tiene progreso cero`() = runTest {
        val listId = seedGuide()
        vm = newVm(listId)
        val hero = vm.hero()
        assertEquals(null, hero?.nowTitle)
        assertEquals(0f, hero?.progress ?: -1f)
    }

    @Test
    fun `historial en orden de visionado e ignora claves borradas`() = runTest {
        val listId = seedGuide()
        db.recentDao().recordWatch(listId, "c2", now - 2_000)
        db.recentDao().recordWatch(listId, "c1", now - 1_000)
        db.recentDao().recordWatch(listId, "fantasma", now)
        vm = newVm(listId)
        assertEquals(listOf("Uno", "Dos"), vm.historyChannels().map { it.displayName })
    }

    @Test
    fun `filtro historial y grupo reparten`() = runTest {
        val listId = seedGuide()
        db.recentDao().recordWatch(listId, "c2", now - 1_000)
        vm = newVm(listId)
        assertEquals(listOf("Dos"), vm.channelsForFilter(Selection.History).map { it.displayName })
        assertEquals(2, vm.channelsForFilter(Selection.All).size)
        assertEquals(2, vm.channelsForFilter(Selection.Group("G")).size)
        assertEquals(0, vm.channelsForFilter(Selection.Group("Otro")).size)
    }
}
