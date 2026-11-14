package com.tvplayer.app.ui.player

import android.os.Looper
import android.widget.ImageView
import com.tvplayer.app.MainActivity
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.data.AppContainer
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.ListEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * T-F4 (hueco tester-2, S04): con canal acestream y sin motor que resuelva el
 * intent de contenido, el botón externo muestra EXACTAMENTE
 * player_external_ace_fail (patrón de VetoMessagesTest con ShadowToast).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerExternalAceFailTest {

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
    fun `acestream sin receptor muestra player_external_ace_fail`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val container = (activity.application as TpApp).container
        val listId = runBlocking {
            container.db.settingsDao().put(AppContainer.KEY_EXTERNAL_GLOBAL, "true")
            val id = container.db.listDao()
                .upsert(ListEntity(name = "L", sourceUrl = "https://x/lista.m3u"))
            container.db.channelDao().upsertAll(
                listOf(
                    ChannelEntity(
                        listId = id, tvgId = "ace1", displayName = "Ace",
                        normName = "ace", groupName = "G",
                        url = "acestream://1a2b3c4d5e6f708192a3b4c5d6e7f00",
                        stableKey = "ace1", position = 0, allowExternal = null,
                    ),
                ),
            )
            id
        }
        activity.pushFlow(PlayerFragment.newInstance(listId, 0), PlayerFragment.TAG)
        pumpUntil {
            activity.supportFragmentManager.findFragmentById(R.id.container) is PlayerFragment &&
                activity.findViewById<ImageView>(R.id.player_external)?.visibility == android.view.View.VISIBLE
        }
        ShadowToast.reset()
        activity.findViewById<ImageView>(R.id.player_external).performClick()
        pumpUntil { ShadowToast.getTextOfLatestToast() != null }
        assertTrue(
            ShadowToast.showedToast(activity.getString(R.string.player_external_ace_fail)),
        )
        assertEquals(
            activity.getString(R.string.player_external_ace_fail),
            ShadowToast.getTextOfLatestToast(),
        )
    }
}
