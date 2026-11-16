package com.zabtv.app.ui.channels

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.MainActivity
import com.zabtv.app.R
import com.zabtv.app.TpApp
import com.zabtv.app.data.db.AppDatabase
import com.zabtv.app.data.db.ChannelEntity
import com.zabtv.app.data.db.ListEntity
import com.zabtv.app.ui.player.PlayerFragment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contención del VER móvil en el hero (cap 176dp): en retrato y en
 * land-expandido el botón queda dentro del hero tras medir+tumbar (con el
 * cap anterior de 140dp desbordaba); el tap navega al player con
 * (listId, position) y el colapso land oculta el body —con el VER dentro—
 * por diseño. Solo layout/ y layout-land; TV intacta (cap 160).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBrowserVerContainTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun pumpUntil(timeoutMs: Long = 20_000, cond: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) break
            Thread.sleep(25)
            looper.idle()
        }
        check(cond()) { "condición sin cumplir tras ${timeoutMs}ms" }
    }

    private fun seedOne(db: AppDatabase): Long = runBlocking {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "https://x/lista.m3u"))
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(
                    listId = listId, tvgId = "c1", displayName = "Uno",
                    normName = "uno", groupName = "G", url = "https://s/1",
                    stableKey = "c1", position = 0,
                ),
            ),
        )
        listId
    }

    /**
     * Borde inferior del VER en coordenadas del hero (acumula los tops
     * intermedios: en land el VER cuelga del body, no del hero directo).
     */
    private fun playBottomInHero(hero: View, play: View): Int {
        var y = play.bottom
        var p = play.parent
        while (p !== hero && p is View) {
            y += p.top
            p = p.parent
        }
        return y
    }

    private fun measureLayout(root: View, wDp: Int, hDp: Int) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(dp(root.context, wDp), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(root.context, hDp), View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, dp(root.context, wDp), dp(root.context, hDp))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `VER retrato contenido en el hero con cap176`() {
        // Con 140dp el VER (borde ~160dp) desbordaba el hero capado.
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val db = (activity.application as TpApp).container.db
        val listId = seedOne(db)
        val fragment = ChannelBrowserFragment.newInstance(listId)
        activity.openTab(fragment, ChannelBrowserFragment.TAG)
        val root = fragment.requireView()
        val hero = root.findViewById<View>(R.id.browser_hero)
        pumpUntil { hero.visibility == View.VISIBLE }
        measureLayout(root, 360, 640)
        val play = root.findViewById<View>(R.id.browser_hero_play)
        val density = root.context.resources.displayMetrics.density
        assertTrue(hero.height <= ChannelBrowserFragment.heroMaxPxFor(density, false))
        assertTrue(playBottomInHero(hero, play) <= hero.height)
        // Con 140dp el VER quedaba aplastado a altura 0 (el LinearLayout
        // mide al último hijo con el resto hasta el cap): visible y completo.
        assertTrue(play.height > 0)
    }

    @Test
    @Config(qualifiers = "land")
    fun `VER land expandido contenido en el hero con cap176`() {
        // heroCollapsed=false de serie: el body pesa y el VER debe caber
        // (con 140dp el borde ~164dp lo recortaba).
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val db = (activity.application as TpApp).container.db
        val listId = seedOne(db)
        val fragment = ChannelBrowserFragment.newInstance(listId)
        activity.openTab(fragment, ChannelBrowserFragment.TAG)
        val root = fragment.requireView()
        val hero = root.findViewById<View>(R.id.browser_hero)
        pumpUntil { hero.visibility == View.VISIBLE }
        root.findViewById<TextView>(R.id.browser_hero_now).text =
            "Línea de guía larga para forzar el ajuste del hero en apaisado con texto extenso 1234567890 abcdefghij"
        measureLayout(root, 640, 360)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.browser_hero_body).visibility)
        val play = root.findViewById<View>(R.id.browser_hero_play)
        val density = root.context.resources.displayMetrics.density
        assertTrue(hero.height <= ChannelBrowserFragment.heroMaxPxFor(density, false))
        assertTrue(playBottomInHero(hero, play) <= hero.height)
        // Con 140dp el VER quedaba aplastado a altura 0 (el LinearLayout
        // mide al último hijo con el resto hasta el cap): visible y completo.
        assertTrue(play.height > 0)
    }

    @Test
    @Config(qualifiers = "land")
    fun `tap VER abre el player y colapsado oculta el body`() {
        // La Activity real ES el NavHost (sin mock: se verifica el efecto,
        // PlayerFragment con los args del canal destacado).
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val db = (activity.application as TpApp).container.db
        val listId = seedOne(db)
        val fragment = ChannelBrowserFragment.newInstance(listId)
        activity.openTab(fragment, ChannelBrowserFragment.TAG)
        val root = fragment.requireView()
        val hero = root.findViewById<View>(R.id.browser_hero)
        pumpUntil { hero.visibility == View.VISIBLE }
        val body = root.findViewById<View>(R.id.browser_hero_body)
        val toggle = root.findViewById<ImageButton>(R.id.browser_hero_toggle)

        // Colapsado land: el body (con el VER dentro) se oculta por diseño.
        toggle.performClick()
        assertEquals(View.GONE, body.visibility)
        toggle.performClick()
        assertEquals(View.VISIBLE, body.visibility)

        // Tap en el VER: el player con (listId, position=0).
        root.findViewById<View>(R.id.browser_hero_play).performClick()
        pumpUntil {
            activity.supportFragmentManager.findFragmentById(R.id.container) is PlayerFragment
        }
        val player = activity.supportFragmentManager
            .findFragmentById(R.id.container) as PlayerFragment
        assertEquals(listId, player.requireArguments().getLong(PlayerFragment.ARG_LIST_ID))
        assertEquals(0, player.requireArguments().getInt(PlayerFragment.ARG_POSITION))
    }
}
