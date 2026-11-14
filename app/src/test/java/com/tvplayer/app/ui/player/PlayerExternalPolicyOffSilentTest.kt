package com.tvplayer.app.ui.player

import android.os.Looper
import android.view.View
import android.widget.ImageView
import com.tvplayer.app.MainActivity
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.data.AppContainer
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.ListEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * T-F6 (rama POLICY_OFF silenciosa en el botón, S04 defensiva inalcanzable por
 * diseño S05): con Global OFF y sin override Siempre el veto es POLICY_OFF y
 * openExternal NO muestra ningún toast ni lanza nada (silencioso, coherente
 * con el INTERNAL silencioso del auto-route). Test de contrato defensivo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerExternalPolicyOffSilentTest {

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

    @Test
    fun `policy off en el boton es silencioso sin toast ni intent`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val container = (activity.application as TpApp).container
        val listId = runBlocking {
            container.db.settingsDao().put(AppContainer.KEY_EXTERNAL_GLOBAL, "false")
            val id = container.db.listDao()
                .upsert(ListEntity(name = "L", sourceUrl = "https://x/lista.m3u"))
            container.db.channelDao().upsertAll(
                listOf(
                    ChannelEntity(
                        listId = id, tvgId = "c1", displayName = "Uno",
                        normName = "uno", groupName = "G",
                        url = "https://cdn.example.com/canal.m3u8",
                        stableKey = "c1", position = 0, allowExternal = null,
                    ),
                ),
            )
            id
        }
        activity.pushFlow(PlayerFragment.newInstance(listId, 0), PlayerFragment.TAG)
        val ext = activity.findViewById<ImageView>(R.id.player_external)
        pumpUntil {
            activity.supportFragmentManager.findFragmentById(R.id.container) is PlayerFragment &&
                ext.visibility == View.GONE
        }
        ShadowToast.reset()
        // performClick funciona aunque el botón esté GONE: ejercita openExternal
        // en la rama POLICY_OFF (el botón no debería ser visible por SECCION-05).
        ext.performClick()
        val looper = shadowOf(Looper.getMainLooper())
        repeat(50) {
            looper.runToNextTask()
            Thread.sleep(20)
        }
        assertNull(
            "POLICY_OFF debe ser silencioso, sin toast",
            ShadowToast.getTextOfLatestToast(),
        )
        assertNull(
            "POLICY_OFF no lanza nada",
            shadowOf(activity).peekNextStartedActivity(),
        )
    }
}
