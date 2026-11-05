package com.tvplayer.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.data.AppContainer
import com.tvplayer.app.data.db.AppDatabase
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.ListEntity
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var service: ImportService
    private lateinit var smallCapService: ImportService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val container = AppContainer(context)
        // Los tests usan MockWebServer (http://localhost): el gate de Task 11 los
        // bloquearía; el seam setAllowHttp existe justo para esto (prod lee SettingsDao).
        container.setAllowHttp(true)
        service = ImportService(context, db, container.okHttp, Dispatchers.IO)
        smallCapService = ImportService(context, db, container.okHttp, Dispatchers.IO, maxBodyBytes = 1000)
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private suspend fun seed100(): Long {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "http://x/lista.m3u"))
        db.channelDao().upsertAll(
            (0 until 100).map { i ->
                ChannelEntity(listId = listId, displayName = "V $i", normName = "v $i", groupName = "Old", url = "http://old/$i", stableKey = "old$i", position = i)
            },
        )
        return listId
    }

    private fun m3u(vararg lines: String) = buildString {
        append("#EXTM3U\n")
        lines.forEach { append(it).append('\n') }
    }

    private fun extinfLine(i: Int, group: Int = i % 7) =
        "#EXTINF:-1 tvg-id=\"ch$i.ext\" group-title=\"G$group\",Canal $i"

    private fun urlLine(i: Int) = "http://srv.example/$i.m3u8"

    @Test
    fun `importa 2003 validos y salta 3 con file`() = runTest {
        val listId = seed100()
        val body = buildString {
            append("#EXTM3U\n")
            repeat(2003) { append(extinfLine(it)).append('\n').append(urlLine(it)).append('\n') }
            repeat(3) {
                append("#EXTINF:-1 tvg-id=\"bad$it.ext\",Bug $it")
                    .append('\n').append("file:///etc/hosts").append('\n')
            }
        }
        server.enqueue(MockResponse().setBody(body))

        val result = service.import(listId, ImportSource.HttpSource(server.url("/list.m3u").toString(), null))

        assertEquals(2003, result.saved)
        assertEquals(3, result.skipped)
        assertTrue(result.log.any { it.contains("file://") })
        assertEquals(2003, db.channelDao().countForList(listId)) // reemplazo total, viejos fuera
        assertFalse(result.rejected)
        assertTrue(result.durationMs >= 0)
    }

    @Test
    fun `acestream se preserva en el import para resolverlo al reproducir`() = runTest {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "http://x/lista.m3u"))
        val body = buildString {
            append("#EXTM3U\n")
            append("#EXTINF:-1 tvg-id=\"ace1\",Ace Uno\n")
            append("acestream://1a2b3c4d5e6f708192a3b4c5d6e7f00\n")
        }
        server.enqueue(MockResponse().setBody(body))

        val result = service.import(listId, ImportSource.HttpSource(server.url("/list.m3u").toString(), null))

        assertEquals(1, result.saved)
        assertEquals(1, db.channelDao().countForList(listId))
        assertEquals(
            "acestream://1a2b3c4d5e6f708192a3b4c5d6e7f00",
            db.channelDao().allForListAndGroup(listId, null).first().url,
        )
    }

    @Test
    fun `reimport devolviendo 0 canales mantiene la tabla y marca rejected`() = runTest {
        val listId = seed100()
        server.enqueue(MockResponse().setBody("#EXTM3U\n"))
        val result = service.import(listId, ImportSource.HttpSource(server.url("/list.m3u").toString(), null))

        assertTrue(result.rejected)
        assertTrue(result.message.orEmpty().contains("10%"))
        assertEquals(100, db.channelDao().countForList(listId))
        assertEquals("V 0", db.channelDao().allForListAndGroup(listId, null).first().displayName) // sigue el viejo
    }

    @Test
    fun `cuerpo mayor que el cap aborta limpio en ambas rutas`() = runTest {
        val listId = seed100()
        val body = buildString {
            append("#EXTM3U\n")
            repeat(40) { append(extinfLine(it)).append('\n').append(urlLine(it)).append('\n') } // ~2.3 KB > cap 1 KB
        }
        // MockWebServer envía framing real (CL ~2.3 KB): si no miente, el abort salta
        // por Content-Length; si es chunked, por el contador de bytes. Misma salida.
        server.enqueue(MockResponse().setBody(body))
        val result = smallCapService.import(listId, ImportSource.HttpSource(server.url("/list.m3u").toString(), null))
        assertTrue(result.rejected)
        assertTrue(result.message.orEmpty().contains("30 MB"))
        assertEquals(100, db.channelDao().countForList(listId))
    }

    @Test
    fun `user agent personalizado llega al servidor`() = runTest {
        val listId = db.listDao().upsert(ListEntity(name = "UA", sourceUrl = "http://x/ua.m3u")) // sin seed: 0 canales previos
        server.enqueue(MockResponse().setBody(m3u("#EXTINF:-1 tvg-id=\"a.ext\",Canal A", "http://s/a.m3u8")))
        val result = service.import(listId, ImportSource.HttpSource(server.url("/list.m3u").toString(), userAgent = "TVPlayer/0.1 (AndroidTV)"))
        assertEquals(1, result.saved)
        assertEquals("TVPlayer/0.1 (AndroidTV)", server.takeRequest().getHeader("User-Agent"))
    }

    @Test
    fun `cancelacion a mitad no deja tabla a medias`() = runTest {
        val listId = seed100()
        // Respuesta con delay de 1 s en el cuerpo: el import queda "a mitad" durante la cancel
        server.enqueue(
            MockResponse()
                .setBodyDelay(1, TimeUnit.SECONDS)
                .setBody(m3u("#EXTINF:-1 tvg-id=\"a.ext\",Canal A", "http://s/a.m3u8")),
        )
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val job = scope.launch { service.import(listId, ImportSource.HttpSource(server.url("/list.m3u").toString(), null)) }
        delay(300)
        job.cancelAndJoin()
        scope.cancel()
        assertEquals(100, db.channelDao().countForList(listId))
        assertEquals("V 0", db.channelDao().allForListAndGroup(listId, null).first().displayName)
    }
}
