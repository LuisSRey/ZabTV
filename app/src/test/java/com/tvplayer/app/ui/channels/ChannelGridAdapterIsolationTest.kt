package com.tvplayer.app.ui.channels

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S3: las 6 celdas pintan títulos o "Sin datos" (nunca el displayName), la
 * tira channel_epg va separada (binds aislados) y las claves EPG son
 * stableKey — una clave tvgId no pinta. TDD RED.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelGridAdapterIsolationTest {

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

    /** stableKey y tvgId DISTINTOS para probar que la clave es stableKey. */
    private fun channel() = ChannelEntity(
        listId = 7, tvgId = "tvg1", displayName = "Uno", normName = "uno",
        groupName = "G", url = "http://s/1", stableKey = "sk1", position = 4,
    )

    private fun programs() = window.slots().let { slots ->
        listOf(EpgProgramEntity(tvgId = "tvg1", startMillis = slots[0], endMillis = slots[1], title = "Ahora"))
    }

    private suspend fun boundHolder(adapter: ChannelGridAdapter): ChannelGridAdapter.Holder {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = RecyclerView(context)
        parent.layoutManager = LinearLayoutManager(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        return holder
    }

    private suspend fun adapterWithChannel(): ChannelGridAdapter {
        val adapter = ChannelGridAdapter(onClick = {})
        adapter.submitData(PagingData.from(listOf(channel())))
        withContext(Dispatchers.IO) {
            withTimeout(10_000) {
                while (adapter.itemCount != 1) kotlinx.coroutines.delay(50)
            }
        }
        return adapter
    }

    private fun cellTexts(holder: ChannelGridAdapter.Holder): List<String> {
        val strip = holder.timeline as LinearLayout
        return (0 until strip.childCount).map { (strip.getChildAt(it) as TextView).text.toString() }
    }

    @Test
    fun `cellsNeverShowChannelName`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val placeholder = context.getString(R.string.browser_empty)
        val adapter = adapterWithChannel()
        adapter.submitTimeline(mapOf("sk1" to emptyList()), window)
        val texts = cellTexts(boundHolder(adapter))
        assertEquals(6, texts.size)
        assertEquals(List(6) { placeholder }, texts)
        assertEquals(true, texts.none { it == "Uno" })
    }

    @Test
    fun `sixCellsAlways tras onBind`() = runTest {
        val adapter = adapterWithChannel()
        adapter.setWindow(window)
        val holder = boundHolder(adapter)
        assertEquals(6, (holder.timeline as LinearLayout).childCount)
        // onBind reescribe las 6 celdas con contenido + descripción.
        for (i in 0 until 6) {
            val cell = (holder.timeline as LinearLayout).getChildAt(i) as TextView
            assertEquals(cell.text.toString(), cell.contentDescription?.toString())
        }
    }

    @Test
    fun `stripVsCellsIsolation`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val placeholder = context.getString(R.string.browser_empty)
        val adapter = adapterWithChannel()
        // La tira pinta sin tocar las celdas.
        adapter.setWindow(window)
        adapter.submitEpg(mapOf("sk1" to "▶ Ahora · Después"))
        var holder = boundHolder(adapter)
        assertEquals("▶ Ahora · Después", holder.epg?.text.toString())
        assertEquals(List(6) { placeholder }, cellTexts(holder))
        // El timeline pinta las celdas sin tocar la tira.
        adapter.submitTimeline(mapOf("sk1" to programs()), window)
        holder = boundHolder(adapter)
        assertEquals("▶ Ahora · Después", holder.epg?.text.toString())
        assertEquals("Ahora", cellTexts(holder)[0])
    }

    @Test
    fun `clave tvgId no pinta solo stableKey`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val placeholder = context.getString(R.string.browser_empty)
        val adapter = adapterWithChannel()
        adapter.setWindow(window)
        // Clave tvgId ("tvg1" != stableKey "sk1"): ni tira ni celdas.
        adapter.submitEpg(mapOf("tvg1" to "▶ Ahora"))
        adapter.submitTimeline(mapOf("tvg1" to programs()), window)
        var holder = boundHolder(adapter)
        assertEquals(View.GONE, holder.epg?.visibility)
        assertEquals(List(6) { placeholder }, cellTexts(holder))
        // Clave stableKey: pinta tira y celdas.
        adapter.submitEpg(mapOf("sk1" to "▶ Ahora"))
        adapter.submitTimeline(mapOf("sk1" to programs()), window)
        holder = boundHolder(adapter)
        assertEquals("▶ Ahora", holder.epg?.text.toString())
        assertEquals("Ahora", cellTexts(holder)[0])
    }
}
