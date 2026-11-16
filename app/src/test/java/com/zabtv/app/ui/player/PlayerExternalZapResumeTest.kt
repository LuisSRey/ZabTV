package com.zabtv.app.ui.player

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import com.zabtv.app.MainActivity
import com.zabtv.app.R
import com.zabtv.app.TpApp
import com.zabtv.app.data.AppContainer
import com.zabtv.app.data.db.ChannelEntity
import com.zabtv.app.data.db.ListEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * T-F5 (hueco tester-3, S05): el botón externo se re-pinta con el override del
 * canal tras zap (global=false: canal1 sin override → GONE; zap a canal2 con
 * override Siempre → VISIBLE) y onResume() lo mantiene. El código ya lo hace
 * (zap() :591, onResume() :355) — si pasa a la primera es verificación.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerExternalZapResumeTest {

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
    fun `zap aplica override del canal y onResume lo mantiene`() {
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
                        url = "https://cdn.example.com/canal1.m3u8",
                        stableKey = "c1", position = 0, allowExternal = null,
                    ),
                    ChannelEntity(
                        listId = id, tvgId = "c2", displayName = "Dos",
                        normName = "dos", groupName = "G",
                        url = "https://cdn.example.com/canal2.m3u8",
                        stableKey = "c2", position = 1, allowExternal = true,
                    ),
                ),
            )
            id
        }
        activity.pushFlow(PlayerFragment.newInstance(listId, 0), PlayerFragment.TAG)
        val ext = activity.findViewById<ImageView>(R.id.player_external)
        val title = activity.findViewById<android.widget.TextView>(R.id.player_title)
        pumpUntil {
            activity.supportFragmentManager.findFragmentById(R.id.container) is PlayerFragment &&
                title.text.toString() == "Uno" && ext.visibility == View.GONE
        }
        // Zap al canal2 (override Siempre): el botón pasa a VISIBLE.
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        pumpUntil { title.text.toString() == "Dos" && ext.visibility == View.VISIBLE }
        assertEquals(View.VISIBLE, ext.visibility)
        // onResume() re-aplica la visibilidad con el canal actual (harness:
        // se corrompe a mano y se invoca directo; el cuerpo es el que corre
        // al volver del diálogo del motor u otra app).
        ext.visibility = View.GONE
        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.container) as PlayerFragment
        fragment.onResume()
        pumpUntil { ext.visibility == View.VISIBLE }
        assertEquals(View.VISIBLE, ext.visibility)
    }
}
