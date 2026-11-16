package com.zabtv.app.data.portal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.data.db.AppDatabase
import com.zabtv.app.data.repo.ImportService
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
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

/**
 * Guía S2: XtreamRepository — checkAuth contra player_api con cap 2 MB +
 * importPortal que solo deja Live (poda VOD/MOVIES/SERIES). TDD RED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XtreamRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var repo: XtreamRepository
    private lateinit var creds: SecureCredentials
    private val client = OkHttpClient.Builder().build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefs = context.getSharedPreferences("test_xtream_repo", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        creds = SecureCredentials(prefs)
        repo = XtreamRepository(db, client, context, creds)
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun host(): String = server.url("/").toString().trimEnd('/')

    private fun authJson(auth: Int) =
        """{"user_info":{"auth":$auth,"status":"Active","exp_date":"1893456000"},"server_info":{"url":"x"}}"""

    @Test
    fun `checkAuth ok con auth 1`() = runTest {
        server.enqueue(MockResponse().setBody(authJson(1)))
        val res = repo.checkAuth(host(), "user", "pass")
        assertTrue(res.authenticated)
    }

    @Test
    fun `checkAuth invalido con auth 0`() = runTest {
        server.enqueue(MockResponse().setBody(authJson(0)))
        val res = repo.checkAuth(host(), "user", "pass")
        assertFalse(res.authenticated)
    }

    @Test
    fun `checkAuth 401 es auth fallido sin tocar DB`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        val res = repo.checkAuth(host(), "user", "wrong")
        assertFalse(res.authenticated)
        assertEquals(0, db.listDao().all().size)
    }

    @Test
    fun `errores nunca llevan credenciales`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        try {
            repo.checkAuth(host(), "miusuario", "misecreto123")
            // 500 no es auth: debe lanzar sin credenciales en el mensaje
            throw AssertionError("esperaba excepción en 500")
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            assertFalse(msg.contains("miusuario"))
            assertFalse(msg.contains("misecreto123"))
        }
    }

    @Test
    fun `cap 2MB aborta auth gigante`() = runTest {
        val tiny = XtreamRepository(db, client, ApplicationProvider.getApplicationContext<Context>(), creds, maxAuthBytes = 100)
        server.enqueue(MockResponse().setBody(authJson(1) + "x".repeat(5000)))
        try {
            tiny.checkAuth(host(), "u", "p")
            throw AssertionError("esperaba aborto por cap")
        } catch (e: Exception) {
            assertTrue(e.message.orEmpty().contains("límite"))
        }
    }

    private fun playlistM3u(): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXTINF:-1 tvg-id=\"l1\" group-title=\"Deportes\",Canal Live 1")
        appendLine(server.url("/l1.m3u8").toString())
        appendLine("#EXTINF:-1 tvg-id=\"l2\" group-title=\"News\",Canal Live 2")
        appendLine(server.url("/l2.m3u8").toString())
        appendLine("#EXTINF:-1 tvg-id=\"v1\" group-title=\"VOD Films\",Peli 1")
        appendLine(server.url("/v1.mp4").toString())
        appendLine("#EXTINF:-1 tvg-id=\"s1\" group-title=\"SERIES-ES\",Serie 1")
        appendLine(server.url("/s1.mp4").toString())
    }

    @Test
    fun `importPortal solo deja Live y guarda creds`() = runTest {
        server.enqueue(MockResponse().setBody(authJson(1)))
        server.enqueue(MockResponse().setBody(playlistM3u()))
        val context = ApplicationProvider.getApplicationContext<Context>()
        // ImportService real sobre el mismo cliente de test (seam: sin gate).
        val svc = ImportService(context, db, client)

        val res = repo.importPortal(null, "Portal", host(), "user", "pass", importService = svc)

        assertFalse(res.rejected)
        assertEquals(2, db.channelDao().countForList(res.listId))
        val groups = db.channelDao().groupsForList(res.listId).map { it.group }
        assertTrue(groups.none { it.contains("VOD", ignoreCase = true) || it.contains("SERIES", ignoreCase = true) })
        assertEquals("user" to "pass", creds.load(res.listId))
    }

    @Test
    fun `importPortal con creds invalidas no toca nada`() = runTest {
        val listId = db.listDao().upsert(
            com.zabtv.app.data.db.ListEntity(name = "L", sourceUrl = "http://x/lista.m3u"),
        )
        db.channelDao().upsertAll(
            listOf(
                com.zabtv.app.data.db.ChannelEntity(
                    listId = listId, displayName = "Vivo", normName = "vivo",
                    groupName = "G", url = "http://s/v", stableKey = "k1", position = 0,
                ),
            ),
        )
        server.enqueue(MockResponse().setBody(authJson(0)))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val res = repo.importPortal(listId, "L", host(), "user", "bad", importService = ImportService(context, db, client))
        assertTrue(res.rejected)
        assertEquals(1, db.channelDao().countForList(listId))
    }

    @Test
    fun `reimport de portal relee creds y actualiza`() = runTest {
        server.enqueue(MockResponse().setBody(authJson(1)))
        server.enqueue(MockResponse().setBody(playlistM3u()))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val svc = ImportService(context, db, client)
        val first = repo.importPortal(null, "Portal", host(), "user", "pass", importService = svc)
        assertFalse(first.rejected)

        // Segunda vuelta: reimport usa host + creds guardadas, sin params nuevos.
        server.enqueue(MockResponse().setBody(authJson(1)))
        server.enqueue(MockResponse().setBody(playlistM3u()))
        val again = repo.reimport(first.listId, importService = svc)
        assertFalse(again.rejected)
        assertEquals(first.listId, again.listId)
        assertEquals(2, db.channelDao().countForList(first.listId))
    }

    @Test
    fun `reimport de lista m3u se rechaza sin tocar nada`() = runTest {
        val listId = db.listDao().upsert(
            com.zabtv.app.data.db.ListEntity(name = "M", sourceUrl = "http://x/lista.m3u"),
        )
        db.channelDao().upsertAll(
            listOf(
                com.zabtv.app.data.db.ChannelEntity(
                    listId = listId, displayName = "Vivo", normName = "vivo",
                    groupName = "G", url = "http://s/v", stableKey = "k1", position = 0,
                ),
            ),
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val res = repo.reimport(listId, importService = ImportService(context, db, client))
        assertTrue(res.rejected)
        assertEquals(1, db.channelDao().countForList(listId))
    }

    @Test
    fun `reimport sin credenciales guardadas se rechaza`() = runTest {
        val listId = db.listDao().upsert(
            com.zabtv.app.data.db.ListEntity(name = "P", sourceUrl = "", portalHost = host()),
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val res = repo.reimport(listId, importService = ImportService(context, db, client))
        assertTrue(res.rejected)
    }
}
