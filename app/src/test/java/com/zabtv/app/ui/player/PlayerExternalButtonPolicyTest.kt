package com.zabtv.app.ui.player

import android.os.Looper
import android.view.View
import android.widget.ImageView
import com.zabtv.app.MainActivity
import com.zabtv.app.R
import com.zabtv.app.TpApp
import com.zabtv.app.data.AppContainer
import com.zabtv.app.data.db.ChannelEntity
import com.zabtv.app.data.db.ListEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * SECCION-05 (T-P0-5 · D4/A3-05, A3-07): el botón externo del player solo
 * existe cuando la política lo permite (Global ON o override Siempre del
 * canal). Con Global OFF y sin override el botón desaparece → imposible el
 * toast «Reproductor externo no permitido» confuso. Los vetos de URL NO
 * ocultan el botón (el motivo del veto es lo que el usuario necesita leer).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerExternalButtonPolicyTest {

    @Test
    fun `a puro visibilidad segun politica`() {
        assertEquals(View.GONE, PlayerFragment.externalButtonVisibility(null, false))
        assertEquals(View.VISIBLE, PlayerFragment.externalButtonVisibility(null, true))
        // override Siempre manda aunque el global esté OFF.
        assertEquals(View.VISIBLE, PlayerFragment.externalButtonVisibility(true, false))
        // override Nunca manda aunque el global esté ON.
        assertEquals(View.GONE, PlayerFragment.externalButtonVisibility(false, true))
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

    /** Monta el player directo (sin auto-route) con el global del DAO. */
    private fun bootPlayer(global: Boolean): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val container = (activity.application as TpApp).container
        val listId = runBlocking {
            container.db.settingsDao().put(AppContainer.KEY_EXTERNAL_GLOBAL, global.toString())
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
        return activity
    }

    private fun assertButton(global: Boolean, expected: Int) {
        val activity = bootPlayer(global)
        val button = activity.findViewById<ImageView>(R.id.player_external)
        pumpUntil {
            activity.supportFragmentManager.findFragmentById(R.id.container) is PlayerFragment &&
                button.visibility == expected
        }
        assertEquals(expected, button.visibility)
    }

    @Test
    fun `b movil global OFF gone`() {
        assertButton(global = false, expected = View.GONE)
    }

    @Test
    fun `b2 movil global ON visible`() {
        assertButton(global = true, expected = View.VISIBLE)
    }

    @Test
    @Config(sdk = [34], qualifiers = "television")
    fun `b3 tv global OFF gone`() {
        assertButton(global = false, expected = View.GONE)
    }

    @Test
    @Config(sdk = [34], qualifiers = "television")
    fun `b4 tv global ON visible`() {
        assertButton(global = true, expected = View.VISIBLE)
    }
}
