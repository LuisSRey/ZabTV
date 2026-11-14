package com.tvplayer.app.ui.channel_dialog

import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import com.tvplayer.app.MainActivity
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.ListEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * SECCION-08 (T-P1-2 · D6/A1-03): la ficha de canal es la ÚNICA pantalla sin
 * variante TV. En TV debe resolverse la variante television (safe area 56dp)
 * y las 5 acciones (play/fav/ext_global/ext_always/ext_never) ser focables
 * con foco escalado (FocusScaleHelper.attach pone el onFocusChangeListener).
 * Se monta el DialogFragment REAL: el attach vive en el wiring del fragment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "television")
class ChannelSheetTvFocusTest {

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
    fun `variante tv con safe area y foco en las 5 acciones`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val container = (activity.application as TpApp).container
        val listId = runBlocking {
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
        val fragment = ChannelSheetFragment.newInstance(listId, "c1")
        fragment.show(activity.supportFragmentManager, ChannelSheetFragment.TAG)
        pumpUntil { fragment.view != null && fragment.view is ScrollView }
        val view = fragment.view as ScrollView
        val inner = view.getChildAt(0) as ViewGroup
        // Safe area 56dp (convención del repo: padding del LinearLayout interior
        // bajo ScrollView fillViewport, igual que fragment_settings TV).
        assertEquals((56 * view.resources.displayMetrics.density).toInt(), inner.paddingStart)
        assertEquals((56 * view.resources.displayMetrics.density).toInt(), inner.paddingTop)

        val buttons = listOf(
            view.findViewById<Button>(R.id.sheet_play),
            view.findViewById<Button>(R.id.sheet_fav),
            view.findViewById<Button>(R.id.sheet_ext_global),
            view.findViewById<Button>(R.id.sheet_ext_always),
            view.findViewById<Button>(R.id.sheet_ext_never),
        )
        for (btn in buttons) {
            assertNotNull("botón sin id en la variante TV", btn)
            assertTrue("${btn.id} debe ser focable en TV", btn.isFocusable)
            assertNotNull(
                "${btn.id} sin onFocusChangeListener (FocusScaleHelper.attach no aplicado)",
                btn.onFocusChangeListener,
            )
        }
    }
}
