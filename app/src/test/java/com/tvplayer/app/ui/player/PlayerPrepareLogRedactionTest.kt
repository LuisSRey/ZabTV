package com.tvplayer.app.ui.player

import android.os.Looper
import com.tvplayer.app.MainActivity
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.data.AppContainer
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.ListEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * T-F1 (security B-1): el log de prepare del player JAMÁS lleva credenciales
 * ni la URL del canal — solo longitud + stableKey (diagnóstico sin PII).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerPrepareLogRedactionTest {

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
    fun `prepare no registra credenciales ni URL del canal`() {
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
                        url = "https://user:pass@cdn.example.com/canal.m3u8",
                        stableKey = "c1", position = 0, allowExternal = null,
                    ),
                ),
            )
            id
        }
        activity.pushFlow(PlayerFragment.newInstance(listId, 0), PlayerFragment.TAG)
        pumpUntil {
            ShadowLog.getLogsForTag("TvPlayer").any { it.msg.contains("prepare ace=") }
        }
        val prepareLogs = ShadowLog.getLogsForTag("TvPlayer")
            .filter { it.msg.contains("prepare ace=") }
        assertTrue(prepareLogs.isNotEmpty())
        for (log in prepareLogs) {
            assertFalse("log filtra credenciales: ${log.msg}", log.msg.contains("user:pass@"))
            assertFalse("log filtra URL/host: ${log.msg}", log.msg.contains("cdn.example.com"))
        }
    }
}
