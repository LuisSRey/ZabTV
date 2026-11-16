package com.zabtv.app

import android.content.Intent
import android.os.Looper
import com.zabtv.app.data.AppContainer
import com.zabtv.app.data.db.AppDatabase
import com.zabtv.app.data.db.ChannelEntity
import com.zabtv.app.data.db.ListEntity
import com.zabtv.app.engine.ExternalOpener
import com.zabtv.app.ui.player.PlayerFragment
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
 * SECCION-04 (T-P0-4 · D3/A3-03): cada veto produce SU mensaje accionable en
 * el auto-route (matriz 2.1.3), no el genérico. Global=true (el usuario lo
 * quería: ruta BLOCKED). Con allow_http=true en DAO el canal http deja de
 * estar vetado y abre externo (chooser). Harness idéntico a
 * MainActivityAutoRouteTest (main real + looper bombeado).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityAutoRouteVetoMessagesTest {

    private val fakePkg = "com.fake.player"

    private lateinit var activity: MainActivity
    private lateinit var db: AppDatabase

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

    private fun containerTag(): String? =
        activity.supportFragmentManager.findFragmentById(R.id.container)?.tag

    private fun seedChannel(url: String) = runBlocking {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "https://x/lista.m3u"))
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(
                    listId = listId, tvgId = "c1", displayName = "Uno",
                    normName = "uno", groupName = "G", url = url,
                    stableKey = "c1", position = 0, allowExternal = null,
                ),
            ),
        )
        listId
    }

    /** Global ON (el usuario lo quería) sin preferido: BLOCKED para todo veto. */
    private fun openSettingsGlobalOn(allowHttp: Boolean) = runBlocking {
        db.settingsDao().put(AppContainer.KEY_EXTERNAL_GLOBAL, "true")
        db.settingsDao().put(AppContainer.KEY_ALLOW_HTTP, allowHttp.toString())
    }

    @Test
    fun `loopback proxificado veto loopback`() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        db = (activity.application as TpApp).container.db
        ShadowToast.reset()
        openSettingsGlobalOn(allowHttp = false)
        val listId = seedChannel("http://127.0.0.1:6878/ace/getstream?id=abc")
        activity.openPlayer(listId, 0)
        pumpUntil { containerTag() == PlayerFragment.TAG }
        assertTrue(
            ShadowToast.showedToast(activity.getString(R.string.player_ext_veto_loopback)),
        )
        assertEquals(
            activity.getString(R.string.player_ext_veto_loopback),
            ShadowToast.getTextOfLatestToast(),
        )
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `rtmp veto url`() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        db = (activity.application as TpApp).container.db
        ShadowToast.reset()
        openSettingsGlobalOn(allowHttp = false)
        val listId = seedChannel("rtmp://srv/live")
        activity.openPlayer(listId, 0)
        pumpUntil { containerTag() == PlayerFragment.TAG }
        assertTrue(
            ShadowToast.showedToast(activity.getString(R.string.player_ext_veto_url)),
        )
        assertEquals(
            activity.getString(R.string.player_ext_veto_url),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun `user info veto userinfo`() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        db = (activity.application as TpApp).container.db
        ShadowToast.reset()
        openSettingsGlobalOn(allowHttp = false)
        val listId = seedChannel("http://user:pass@host/x")
        activity.openPlayer(listId, 0)
        pumpUntil { containerTag() == PlayerFragment.TAG }
        assertTrue(
            ShadowToast.showedToast(activity.getString(R.string.player_ext_veto_userinfo)),
        )
        assertEquals(
            activity.getString(R.string.player_ext_veto_userinfo),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun `http sin allow veto cleartext`() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        db = (activity.application as TpApp).container.db
        ShadowToast.reset()
        openSettingsGlobalOn(allowHttp = false)
        val listId = seedChannel("http://cdn.example.com/canal.m3u8")
        activity.openPlayer(listId, 0)
        pumpUntil { containerTag() == PlayerFragment.TAG }
        assertTrue(
            ShadowToast.showedToast(activity.getString(R.string.player_ext_veto_cleartext)),
        )
        assertEquals(
            activity.getString(R.string.player_ext_veto_cleartext),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun `http con allow_http en DAO abre externo`() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        db = (activity.application as TpApp).container.db
        ShadowToast.reset()
        val url = "http://cdn.example.com/canal.m3u8"
        openSettingsGlobalOn(allowHttp = true)
        val listId = seedChannel(url)
        // Handler registrado para el genérico (espejo de filter real video).
        val info = org.robolectric.shadows.ShadowPackageManager().let {
            android.content.pm.ResolveInfo().apply {
                activityInfo = android.content.pm.ActivityInfo().apply {
                    packageName = fakePkg
                    name = "$fakePkg.PlayActivity"
                }
            }
        }
        val pm = shadowOf(activity.packageManager)
        pm.addResolveInfoForIntent(ExternalOpener.chooserIntent(url), info)
        pm.addResolveInfoForIntent(
            Intent(ExternalOpener.chooserIntent(url)).setPackage(fakePkg), info,
        )
        activity.openPlayer(listId, 0)
        pumpUntil { shadowOf(activity).peekNextStartedActivity() != null }
        assertEquals(Intent.ACTION_CHOOSER, shadowOf(activity).nextStartedActivity.action)
        assertTrue(containerTag() != PlayerFragment.TAG)
    }
}
