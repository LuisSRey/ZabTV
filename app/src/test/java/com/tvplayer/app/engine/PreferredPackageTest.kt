package com.tvplayer.app.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.data.AppContainer
import com.tvplayer.app.data.db.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Paquete preferido del reproductor externo (key external_player_package,
 * "" = Preguntar siempre): roundtrip en SettingsDao, directo sin chooser si
 * el paquete resuelve, fallback a chooser si se desinstala, y el override
 * por canal sigue mandando sobre el global (canExport intacto).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreferredPackageTest {

    private lateinit var db: AppDatabase
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val fakePkg = "com.fake.player"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // Un reproductor falso que resuelve el intent VIEW del canal.
        // El shadow compara por igualdad de intent: se registra el mismo
        // que construye ExternalOpener (genérico y dirigido al paquete).
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = fakePkg
                name = "$fakePkg.PlayActivity"
            }
        }
        val shadowPm = shadowOf(context.packageManager)
        shadowPm.addResolveInfoForIntent(channelIntent(), info)
        shadowPm.addResolveInfoForIntent(
            channelIntent().apply { setPackage(fakePkg) },
            info,
        )
    }

    private fun channelIntent(): Intent =
        ExternalOpener.buildIntent("https://cdn.example.com/canal.m3u8", null)

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `set get roundtrip y vacio es preguntar siempre`() = runTest {
        val dao = db.settingsDao()
        val key = AppContainer.KEY_EXTERNAL_PLAYER_PACKAGE
        assertNull(ExternalPlayers.getPreferred(dao, key))
        ExternalPlayers.setPreferred(dao, key, fakePkg)
        assertEquals(fakePkg, ExternalPlayers.getPreferred(dao, key))
        ExternalPlayers.setPreferred(dao, key, null)
        assertNull(ExternalPlayers.getPreferred(dao, key))
        assertEquals("", dao.get(AppContainer.KEY_EXTERNAL_PLAYER_PACKAGE, "x"))
    }

    @Test
    fun `paquete en blanco no es utilizable`() {
        val pm = context.packageManager
        assertFalse(ExternalPlayers.isPreferredUsable(pm, null, channelIntent()))
        assertFalse(ExternalPlayers.isPreferredUsable(pm, "", channelIntent()))
        assertFalse(ExternalPlayers.isPreferredUsable(pm, "com.gone.app", channelIntent()))
    }

    @Test
    fun `paquete instalado es utilizable`() {
        assertTrue(ExternalPlayers.isPreferredUsable(context.packageManager, fakePkg, channelIntent()))
    }

    private fun lastStarted(): Intent =
        shadowOf(context as android.app.Application).nextStartedActivity

    @Test
    fun `preferido valido abre directo sin chooser`() {
        val opened = ExternalOpener.openChannel(
            context, "https://cdn.example.com/canal.m3u8", source = null,
            allowHttp = false, engineProxyFor = null, override = null,
            global = true, preferredPackage = fakePkg,
        )
        assertTrue(opened)
        val started = lastStarted()
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(fakePkg, started.`package`)
    }

    @Test
    fun `paquete desinstalado cae a chooser`() {
        val opened = ExternalOpener.openChannel(
            context, "https://cdn.example.com/canal.m3u8", source = null,
            allowHttp = false, engineProxyFor = null, override = null,
            global = true, preferredPackage = "com.gone.app",
        )
        assertTrue(opened)
        assertEquals(Intent.ACTION_CHOOSER, lastStarted().action)
    }

    @Test
    fun `sin preferido usa chooser como antes`() {
        val opened = ExternalOpener.openChannel(
            context, "https://cdn.example.com/canal.m3u8", source = null,
            allowHttp = false, engineProxyFor = null, override = null,
            global = true, preferredPackage = null,
        )
        assertTrue(opened)
        assertEquals(Intent.ACTION_CHOOSER, lastStarted().action)
    }

    @Test
    fun `override Nunca veta aunque haya preferido`() {
        val opened = ExternalOpener.openChannel(
            context, "https://cdn.example.com/canal.m3u8", source = null,
            allowHttp = false, engineProxyFor = null, override = false,
            global = true, preferredPackage = fakePkg,
        )
        assertFalse(opened)
    }
}
