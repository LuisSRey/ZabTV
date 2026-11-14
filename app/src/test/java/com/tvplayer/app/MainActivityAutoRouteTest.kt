package com.tvplayer.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import com.tvplayer.app.data.AppContainer
import com.tvplayer.app.data.db.AppDatabase
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.ListEntity
import com.tvplayer.app.engine.ExternalOpener
import com.tvplayer.app.ui.player.PlayerFragment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * CA5: openPlayer AUTO al reproductor externo con fallback interno —
 * directo con preferido usable, chooser con Preguntar, gone→toast+fallback,
 * no_external→toast+interno, blocked→toast+interno. Robolectric sobre la
 * Activity real (sin Main Unconfined: el looper se bombea por tareas y se
 * para al observar el efecto, sin arrancar el motor del PlayerFragment).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityAutoRouteTest {

    private val fakePkg = "com.fake.player"
    private val httpsUrl = "https://cdn.example.com/canal.m3u8"
    private val httpsMime = "application/vnd.apple.mpegurl"

    private lateinit var activity: MainActivity
    private lateinit var db: AppDatabase

    private fun setUp() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        db = (activity.application as TpApp).container.db
        ShadowToast.reset()
    }

    @After
    fun tearDown() {
        ShadowToast.reset()
    }

    private fun pumpUntil(timeoutMs: Long = 15_000, cond: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            check(System.currentTimeMillis() <= end) { "efecto sin observar" }
            looper.runToNextTask()
            Thread.sleep(20)
        }
    }

    private suspend fun seedChannel(url: String, allowExternal: Boolean? = null): Long {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "https://x/lista.m3u"))
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(
                    listId = listId, tvgId = "c1", displayName = "Uno",
                    normName = "uno", groupName = "G", url = url,
                    stableKey = "c1", position = 0, allowExternal = allowExternal,
                ),
            ),
        )
        return listId
    }

    private suspend fun setPolicy(global: Boolean, preferred: String?) {
        db.settingsDao().put(AppContainer.KEY_EXTERNAL_GLOBAL, global.toString())
        db.settingsDao().put(AppContainer.KEY_EXTERNAL_PLAYER_PACKAGE, preferred.orEmpty())
    }

    /**
     * SECCION-03 (D2): registra el handler TAMBIÉN para el chooserIntent
     * (genérico de vídeo) — espejo de un intent-filter real, que trae el
     * tipo genérico además del exacto. Los 6 casos previos no cambian.
     */
    private fun registerPlayer(url: String, mime: String?, pkg: String) {
        registerIntent(ExternalOpener.buildIntent(url, mime), pkg)
        registerIntent(ExternalOpener.chooserIntent(url), pkg)
    }

    private fun registerIntent(intent: Intent, pkg: String) {
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.PlayActivity"
            }
        }
        val shadowPm = shadowOf(activity.packageManager)
        shadowPm.addResolveInfoForIntent(intent, info)
        shadowPm.addResolveInfoForIntent(
            Intent(intent).setPackage(pkg),
            info,
        )
    }

    private fun containerTag(): String? =
        activity.supportFragmentManager.findFragmentById(R.id.container)?.tag

    @Test
    fun `AUTO directo con preferido usable`() {
        setUp()
        val listId = runBlocking {
            setPolicy(global = true, preferred = fakePkg)
            seedChannel(httpsUrl)
        }
        registerPlayer(httpsUrl, httpsMime, fakePkg)
        activity.openPlayer(listId, 0)
        pumpUntil { shadowOf(activity).peekNextStartedActivity() != null }
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(fakePkg, started.`package`)
        // Sin fallback: el browser/home sigue al mando.
        assertTrue(containerTag() != PlayerFragment.TAG)
    }

    @Test
    fun `AUTO directo con preferido solo video`() {
        setUp()
        val listId = runBlocking {
            setPolicy(global = true, preferred = fakePkg)
            seedChannel(httpsUrl)
        }
        // App cuyo filter solo declara el genérico de vídeo (SECCION-03): el
        // export con candidatos la resuelve igual (antes fallaba el MIME exacto).
        registerIntent(ExternalOpener.chooserIntent(httpsUrl), fakePkg)
        activity.openPlayer(listId, 0)
        pumpUntil { shadowOf(activity).peekNextStartedActivity() != null }
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(fakePkg, started.`package`)
        assertTrue(containerTag() != PlayerFragment.TAG)
    }

    @Test
    fun `AUTO chooser con Preguntar siempre`() {
        setUp()
        val listId = runBlocking {
            setPolicy(global = true, preferred = null)
            seedChannel(httpsUrl)
        }
        registerPlayer(httpsUrl, httpsMime, fakePkg)
        activity.openPlayer(listId, 0)
        pumpUntil { shadowOf(activity).peekNextStartedActivity() != null }
        assertEquals(Intent.ACTION_CHOOSER, shadowOf(activity).nextStartedActivity.action)
        assertTrue(containerTag() != PlayerFragment.TAG)
    }

    @Test
    fun `AUTO gone toast mas fallback interno`() {
        setUp()
        val listId = runBlocking {
            setPolicy(global = true, preferred = "com.gone.app")
            seedChannel(httpsUrl)
        }
        // Sin receptor registrado: el chooser falla tras el aviso de gone.
        activity.openPlayer(listId, 0)
        pumpUntil { containerTag() == PlayerFragment.TAG }
        // Dos avisos en orden (gone y luego sin-app); el último es sin-app.
        assertEquals(2, ShadowToast.shownToastCount())
        assertTrue(ShadowToast.showedToast(activity.getString(R.string.settings_external_gone)))
        assertEquals(
            activity.getString(R.string.player_no_external),
            ShadowToast.getTextOfLatestToast(),
        )
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `AUTO gone con receptor avisa y abre chooser`() {
        setUp()
        val listId = runBlocking {
            setPolicy(global = true, preferred = "com.gone.app")
            seedChannel(httpsUrl)
        }
        registerPlayer(httpsUrl, httpsMime, fakePkg)
        activity.openPlayer(listId, 0)
        pumpUntil { shadowOf(activity).peekNextStartedActivity() != null }
        assertEquals(
            activity.getString(R.string.settings_external_gone),
            ShadowToast.getTextOfLatestToast(),
        )
        assertEquals(Intent.ACTION_CHOOSER, shadowOf(activity).nextStartedActivity.action)
        assertTrue(containerTag() != PlayerFragment.TAG)
    }

    @Test
    fun `AUTO sin receptor toast mas interno`() {
        setUp()
        val listId = runBlocking {
            setPolicy(global = true, preferred = null)
            seedChannel(httpsUrl)
        }
        activity.openPlayer(listId, 0)
        pumpUntil { containerTag() == PlayerFragment.TAG }
        assertTrue(ShadowToast.showedToast(activity.getString(R.string.player_no_external)))
        assertEquals(
            activity.getString(R.string.player_no_external),
            ShadowToast.getTextOfLatestToast(),
        )
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `AUTO bloqueado toast mas interno`() {
        setUp()
        val listId = runBlocking {
            setPolicy(global = true, preferred = null)
            seedChannel("http://cdn.example.com/canal.m3u8")
        }
        activity.openPlayer(listId, 0)
        pumpUntil { containerTag() == PlayerFragment.TAG }
        assertTrue(ShadowToast.showedToast(activity.getString(R.string.player_external_blocked)))
        assertEquals(
            activity.getString(R.string.player_external_blocked),
            ShadowToast.getTextOfLatestToast(),
        )
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `AUTO global OFF va a interno sin toast`() {
        setUp()
        val listId = runBlocking {
            setPolicy(global = false, preferred = null)
            seedChannel(httpsUrl)
        }
        activity.openPlayer(listId, 0)
        pumpUntil { containerTag() == PlayerFragment.TAG }
        assertEquals(0, ShadowToast.shownToastCount())
        assertNull(shadowOf(activity).nextStartedActivity)
    }
}
