package com.tvplayer.app.ui.channels

import android.content.Context
import android.widget.LinearLayout
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.core.xmltv.GuideWindow
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.EpgProgramEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fusión Guía→Canales (S2): la fila del adapter lleva nº + logo + nombre +
 * 6 celdas del timeline ([GuideWindow]); submitEpg y submitTimeline
 * actualizan por payloads sin recargar la grilla; peek intacto. TDD RED.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelGridAdapterTest {

    private val anchor = 1_794_294_600_000L // 2026-11-10T19:10:00Z, corte :30/:00
    private val window = GuideWindow(GuideWindow.anchorFor(anchor + 32L * 60_000L))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun channel() = ChannelEntity(
        listId = 7, tvgId = "c1", displayName = "Uno", normName = "uno",
        groupName = "G", url = "http://s/1", stableKey = "c1", position = 4,
    )

    @Test
    fun `cellTitles reparte un titulo por corte`() {
        val slots = window.slots()
        val programs = listOf(
            EpgProgramEntity(tvgId = "c1", startMillis = slots[0], endMillis = slots[1], title = "Primero"),
            EpgProgramEntity(tvgId = "c1", startMillis = slots[2] + 60_000, endMillis = slots[4], title = "Tarde"),
        )
        val titles = ChannelGridAdapter.cellTitles(programs, window)
        assertEquals(6, titles.size)
        assertEquals("Primero", titles[0])
        assertEquals("", titles[1])
        assertEquals("Tarde", titles[2])
        assertEquals("Tarde", titles[3])
        assertEquals("", titles[4])
        assertEquals("", titles[5])
    }

    @Test
    fun `cellTitles vacio son 6 huecos`() {
        assertEquals(List(6) { "" }, ChannelGridAdapter.cellTitles(emptyList(), window))
    }

    @Test
    fun `fila pinta numero nombre y 6 celdas tras submitTimeline`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = ChannelGridAdapter(onClick = {})
        adapter.submitData(PagingData.from(listOf(channel())))
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                while (adapter.itemCount != 1) kotlinx.coroutines.delay(50)
            }
        }
        assertEquals("c1", adapter.peek(0)?.tvgId)
        val slots = window.slots()
        adapter.submitTimeline(
            mapOf("c1" to listOf(EpgProgramEntity(tvgId = "c1", startMillis = slots[0], endMillis = slots[1], title = "Ahora"))),
            window,
        )
        val parent = RecyclerView(context)
        parent.layoutManager = LinearLayoutManager(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        assertEquals("5", holder.number?.text.toString())
        assertEquals("Uno", holder.name.text.toString())
        val cells = (holder.timeline as LinearLayout)
        assertEquals(6, cells.childCount)
        assertEquals("Ahora", (cells.getChildAt(0) as android.widget.TextView).text.toString())
        assertEquals(context.getString(com.tvplayer.app.R.string.browser_empty), (cells.getChildAt(1) as android.widget.TextView).text.toString())
    }

    @Test
    fun `sin ventana el timeline se oculta (rail Home)`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = ChannelGridAdapter(onClick = {})
        adapter.submitData(PagingData.from(listOf(channel())))
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                while (adapter.itemCount != 1) kotlinx.coroutines.delay(50)
            }
        }
        val parent = RecyclerView(context)
        parent.layoutManager = LinearLayoutManager(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        assertEquals(android.view.View.GONE, holder.timeline?.visibility)
    }

    @Test
    fun `timeline visible con placeholder sin datos cuando no hay guia`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = ChannelGridAdapter(onClick = {})
        adapter.submitData(PagingData.from(listOf(channel())))
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                while (adapter.itemCount != 1) kotlinx.coroutines.delay(50)
            }
        }
        // Ventana presente pero sin programas: visible y placeholder existente.
        adapter.submitTimeline(mapOf("c1" to emptyList()), window)
        val parent = RecyclerView(context)
        parent.layoutManager = LinearLayoutManager(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        assertEquals(android.view.View.VISIBLE, holder.timeline?.visibility)
        val cells = holder.timeline as LinearLayout
        // Placeholder = string existente de vacío (es "Sin datos"), nunca "—".
        val placeholder = context.getString(com.tvplayer.app.R.string.browser_empty)
        assertEquals(placeholder, (cells.getChildAt(0) as android.widget.TextView).text.toString())
        for (i in 0 until cells.childCount) {
            val text = (cells.getChildAt(i) as android.widget.TextView).text.toString()
            assertTrue(!text.contains("—"))
        }
    }

    @Test
    fun `holder sin numero ni timeline no crashea (protege TV)`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = ChannelGridAdapter(onClick = {})
        adapter.submitData(PagingData.from(listOf(channel())))
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                while (adapter.itemCount != 1) kotlinx.coroutines.delay(50)
            }
        }
        // Vista estilo TV: sin channel_number ni channel_timeline.
        val root = LinearLayout(context)
        val logo = android.widget.ImageView(context)
        logo.id = com.tvplayer.app.R.id.channel_logo
        root.addView(logo)
        val name = android.widget.TextView(context)
        name.id = com.tvplayer.app.R.id.channel_name
        root.addView(name)
        val holder = ChannelGridAdapter.Holder(root)
        adapter.onBindViewHolder(holder, 0)
        // Sin ventana (Home) y con ventana: nunca crashea sin timeline.
        adapter.submitTimeline(mapOf("c1" to emptyList()), window)
        adapter.onBindViewHolder(holder, 0)
    }

    @Test
    fun `setWindow pinta tira visible con placeholder sin submitTimeline`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = ChannelGridAdapter(onClick = {})
        adapter.submitData(PagingData.from(listOf(channel())))
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                while (adapter.itemCount != 1) kotlinx.coroutines.delay(50)
            }
        }
        // Solo ventana, sin lote: visible con placeholder (no GONE).
        adapter.setWindow(window)
        val parent = RecyclerView(context)
        parent.layoutManager = LinearLayoutManager(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        assertEquals(android.view.View.VISIBLE, holder.timeline?.visibility)
        val cells = holder.timeline as LinearLayout
        val placeholder = context.getString(com.tvplayer.app.R.string.browser_empty)
        assertEquals(placeholder, (cells.getChildAt(0) as android.widget.TextView).text.toString())
    }

    @Test
    fun `submitTimeline con mapa vacio limpia y delega en setWindow`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = ChannelGridAdapter(onClick = {})
        adapter.submitData(PagingData.from(listOf(channel())))
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                while (adapter.itemCount != 1) kotlinx.coroutines.delay(50)
            }
        }
        val slots = window.slots()
        adapter.submitTimeline(
            mapOf("c1" to listOf(EpgProgramEntity(tvgId = "c1", startMillis = slots[0], endMillis = slots[1], title = "Ahora"))),
            window,
        )
        val parent = RecyclerView(context)
        parent.layoutManager = LinearLayoutManager(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        val cells = holder.timeline as LinearLayout
        assertEquals("Ahora", (cells.getChildAt(0) as android.widget.TextView).text.toString())
        // Mapa vacío aceptado: limpia el lote pero conserva la ventana visible.
        adapter.submitTimeline(emptyMap(), window)
        adapter.onBindViewHolder(holder, 0)
        assertEquals(android.view.View.VISIBLE, holder.timeline?.visibility)
        val placeholder = context.getString(com.tvplayer.app.R.string.browser_empty)
        assertEquals(placeholder, (cells.getChildAt(0) as android.widget.TextView).text.toString())
    }

    @Test
    fun `submitEpg sigue pintando la tira tras el timeline`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = ChannelGridAdapter(onClick = {})
        adapter.submitData(PagingData.from(listOf(channel())))
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                while (adapter.itemCount != 1) kotlinx.coroutines.delay(50)
            }
        }
        adapter.submitTimeline(mapOf("c1" to emptyList()), window)
        adapter.submitEpg(mapOf("c1" to "▶ Ahora · Después"))
        val parent = RecyclerView(context)
        parent.layoutManager = LinearLayoutManager(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        assertEquals("▶ Ahora · Después", holder.epg?.text.toString())
        assertEquals(android.view.View.VISIBLE, holder.epg?.visibility)
    }
}
