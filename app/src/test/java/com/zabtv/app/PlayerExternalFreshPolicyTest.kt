package com.zabtv.app

import android.content.Intent
import android.os.Looper
import android.widget.ImageView
import android.view.View
import com.zabtv.app.data.AppContainer
import com.zabtv.app.data.db.ChannelEntity
import com.zabtv.app.data.db.ListEntity
import com.zabtv.app.engine.ExternalOpener
import com.zabtv.app.ui.player.PlayerFragment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * SECCION-06 (T-P0-6 · D11/A3-04, A2-02): el botón del player decide con la
 * política FRESCA del DAO, no con la caché currentPolicy. Seed DAO
 * external_global=true + allow_http=true SIN refreshNetPolicy → currentPolicy
 * queda rancio (false/false): la versión vieja vetaba por POLICY_OFF y
 * callaba; la fresca abre el externo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerExternalFreshPolicyTest {

    private val url = "https://cdn.example.com/canal.m3u8"
    private val fakePkg = "com.fake.player"

    private lateinit var activity: MainActivity

    private fun pumpUntil(timeoutMs: Long = 15_000, cond: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            check(System.currentTimeMillis() <= end) { "efecto sin observar" }
            looper.runToNextTask()
            Thread.sleep(20)
        }
    }

    @Test
    fun `boton del player decide con DAO fresco sin refresh`() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val container = (activity.application as TpApp).container
        val listId = runBlocking {
            container.db.settingsDao().put(AppContainer.KEY_EXTERNAL_GLOBAL, "true")
            container.db.settingsDao().put(AppContainer.KEY_ALLOW_HTTP, "true")
            val id = container.db.listDao()
                .upsert(ListEntity(name = "L", sourceUrl = "https://x/lista.m3u"))
            container.db.channelDao().upsertAll(
                listOf(
                    ChannelEntity(
                        listId = id, tvgId = "c1", displayName = "Uno",
                        normName = "uno", groupName = "G", url = url,
                        stableKey = "c1", position = 0, allowExternal = null,
                    ),
                ),
            )
            id
        }
        // Handler para el genérico (espejo de filter real video).
        val info = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = fakePkg
                name = "$fakePkg.PlayActivity"
            }
        }
        val pm = shadowOf(activity.packageManager)
        pm.addResolveInfoForIntent(ExternalOpener.chooserIntent(url), info)
        pm.addResolveInfoForIntent(
            Intent(ExternalOpener.chooserIntent(url)).setPackage(fakePkg), info,
        )
        activity.pushFlow(PlayerFragment.newInstance(listId, 0), PlayerFragment.TAG)
        val button = activity.findViewById<ImageView>(R.id.player_external)
        pumpUntil {
            activity.supportFragmentManager.findFragmentById(R.id.container) is PlayerFragment &&
                button.visibility == View.VISIBLE
        }
        // currentPolicy está rancio (sin refresh): el botón AUN ASÍ abre externo.
        button.performClick()
        pumpUntil { shadowOf(activity).peekNextStartedActivity() != null }
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, started.action)
    }
}
