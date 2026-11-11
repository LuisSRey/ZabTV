package com.tvplayer.app.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.data.AppContainer
import com.tvplayer.app.data.db.AppDatabase
import com.tvplayer.app.engine.ExternalAutoRoute.Route
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S5: ExternalAutoRoute — tabla pura de auto-route al reproductor externo.
 * Vetos de ExternalGate intactos; openPlayer cae a interno + toast si la
 * ruta externa falla o está vetada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExternalAutoRouteTest {

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

    private fun route(
        url: String = "https://cdn.example.com/canal.m3u8",
        allowHttp: Boolean = false,
        engineProxy: String? = null,
        override: Boolean? = null,
        global: Boolean = false,
        preferred: String? = null,
        preferredUsable: Boolean = false,
    ) = ExternalAutoRoute.resolve(url, allowHttp, engineProxy, override, global, preferred, preferredUsable)

    @Test
    fun `global OFF va a interno sin toast`() {
        assertEquals(Route.INTERNAL, route(global = false))
    }

    @Test
    fun `override Nunca va a interno aunque global ON`() {
        assertEquals(Route.INTERNAL, route(override = false, global = true))
    }

    @Test
    fun `global ON sin preferido va a chooser`() {
        assertEquals(Route.EXTERNAL_CHOOSER, route(global = true))
    }

    @Test
    fun `global ON con preferido usable va directo`() {
        assertEquals(
            Route.EXTERNAL_DIRECT,
            route(global = true, preferred = "com.fake.player", preferredUsable = true),
        )
    }

    @Test
    fun `preferido puesto pero no usable cae a chooser`() {
        assertEquals(
            Route.EXTERNAL_CHOOSER,
            route(global = true, preferred = "com.gone.app", preferredUsable = false),
        )
    }

    @Test
    fun `override Siempre gana a global OFF`() {
        assertEquals(Route.EXTERNAL_CHOOSER, route(override = true, global = false))
    }

    @Test
    fun `http con cleartext OFF y global ON bloquea con fallback`() {
        assertEquals(
            Route.BLOCKED,
            route(url = "http://cdn.example.com/canal.m3u8", allowHttp = false, global = true),
        )
    }

    @Test
    fun `http con cleartext ON y global ON va a chooser`() {
        assertEquals(
            Route.EXTERNAL_CHOOSER,
            route(url = "http://cdn.example.com/canal.m3u8", allowHttp = true, global = true),
        )
    }

    @Test
    fun `acestream va a interno via propia aunque global ON`() {
        assertEquals(Route.INTERNAL, route(url = "acestream://deadbeef1234", global = true))
        assertEquals(
            Route.INTERNAL,
            route(url = "acestream://deadbeef1234", override = true, global = true),
        )
    }

    @Test
    fun `vetos intactos esquema raro y loopback bloquean con Siempre`() {
        assertEquals(
            Route.BLOCKED,
            route(url = "rtmp://cdn.example.com/live", override = true, global = false),
        )
        assertEquals(
            Route.BLOCKED,
            route(
                url = "http://127.0.0.1:6878/ace/getstream?id=deadbeef",
                allowHttp = true, engineProxy = "127.0.0.1:6878",
                override = true, global = false,
            ),
        )
    }

    @Test
    fun `freshPolicyForRoute lee defaults de DAO`() = runTest {
        val (allowHttp, global, preferred) =
            ExternalAutoRoute.freshPolicyForRoute(db.settingsDao())
        assertEquals(false, allowHttp)
        assertEquals(false, global)
        assertEquals(null, preferred)
    }

    @Test
    fun `freshPolicyForRoute lee valores vigentes no cache`() = runTest {
        val dao = db.settingsDao()
        dao.put(AppContainer.KEY_ALLOW_HTTP, "true")
        dao.put(AppContainer.KEY_EXTERNAL_GLOBAL, "true")
        dao.put(AppContainer.KEY_EXTERNAL_PLAYER_PACKAGE, "com.fake.player")
        val (allowHttp, global, preferred) = ExternalAutoRoute.freshPolicyForRoute(dao)
        assertEquals(true, allowHttp)
        assertEquals(true, global)
        assertEquals("com.fake.player", preferred)
    }
}
