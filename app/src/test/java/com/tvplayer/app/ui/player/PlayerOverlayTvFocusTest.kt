package com.tvplayer.app.ui.player

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.MainActivity
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.data.AppContainer
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.ListEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * SECCION-07 (T-P1-1 · D5/A1-02): ruta de foco TV del overlay del player.
 * Modelo de dos zonas: la raíz concentra el foco cuando el overlay está
 * GONE (Zona A); con el overlay visible, D-pad alcanza
 * rew↔playpause↔ff↔external (Zona B) con zap por UP/DOWN SIN perder el
 * foco, teclas multimedia y BACK intactos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "television")
class PlayerOverlayTvFocusTest {

    // ---- (a) XML: cadena de foco explícita ----

    private fun tvLayoutText(): String =
        File("src/main/res/layout-television/fragment_player.xml").readText()

    @Test
    fun `a cadena de foco del transporte en XML`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val parent = android.widget.FrameLayout(context)
        val root = android.view.LayoutInflater.from(context)
            .inflate(R.layout.fragment_player, parent, false)
        val rew = root.findViewById<View>(R.id.player_rew)
        val play = root.findViewById<View>(R.id.player_playpause)
        val ff = root.findViewById<View>(R.id.player_ff)
        val ext = root.findViewById<View>(R.id.player_external)
        assertEquals(R.id.player_playpause, rew.nextFocusRightId)
        assertEquals(R.id.player_rew, play.nextFocusLeftId)
        assertEquals(R.id.player_ff, play.nextFocusRightId)
        assertEquals(R.id.player_playpause, ff.nextFocusLeftId)
        assertEquals(R.id.player_external, ff.nextFocusRightId)
        assertEquals(R.id.player_ff, ext.nextFocusLeftId)
    }

    @Test
    fun `a2 tile_focus_bg en rew ff y external`() {
        val xml = tvLayoutText()
        for (id in listOf("player_rew", "player_ff", "player_external")) {
            val node = Regex("""<ImageView\s*[^>]*android:id="@\+id/$id"[^>]*>""").find(xml)
                ?.value ?: throw AssertionError("nodo $id no encontrado")
            assertTrue("$id sin tile_focus_bg", node.contains("@drawable/tile_focus_bg"))
        }
    }

    @Test
    fun `a3 hint tv del overlay actualizado`() {
        assertTrue(
            tvLayoutText().contains("@string/player_zapping_hint_tv"),
        )
        // Robolectric resuelve EN por defecto (qualifiers television no fijan
        // locale): texto EN resuelto + presencia del texto ES por archivo.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(
            "OK shows the controls · ↑/↓ changes channel · BACK to exit",
            context.getString(R.string.player_zapping_hint_tv),
        )
        assertTrue(
            File("src/main/res/values/strings.xml").readText()
                .contains("OK muestra los controles · ↑/↓ cambia de canal · BACK para salir"),
        )
        assertTrue(
            File("src/main/res/values-en/strings.xml").readText()
                .contains("OK shows the controls · ↑/↓ changes channel · BACK to exit"),
        )
    }

    // ---- (b) comportamiento con Activity + fragmento ----

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

    private fun down(code: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
    }

    /**
     * Movimiento direccional: lo que ViewRootImpl.processKeyEvent hace con un
     * DPAD no consumido (focusSearch + requestFocus en la dirección). El
     * destino lo define la cadena nextFocus* del XML, fijada por el test (a).
     * activity.dispatchKeyEvent consume teclas pero no navega el foco: el
     * pipeline de ViewRootImpl no corre en Robolectric (limitación del
     * harness, documentada en el ledger).
     */
    private fun nav(view: View, dir: Int) {
        view.focusSearch(dir)?.requestFocus(dir)
    }

    private fun seed(): Long {
        val container = (activity.application as TpApp).container
        return runBlocking {
            container.db.settingsDao().put(AppContainer.KEY_EXTERNAL_GLOBAL, "true")
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
                        stableKey = "c2", position = 1, allowExternal = null,
                    ),
                ),
            )
            id
        }
    }

    private fun boot(): Triple<com.tvplayer.app.ui.player.PlayerFragment, List<ImageView>, View> {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val listId = seed()
        activity.pushFlow(PlayerFragment.newInstance(listId, 0), PlayerFragment.TAG)
        pumpUntil {
            activity.supportFragmentManager.findFragmentById(R.id.container) is PlayerFragment
        }
        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.container) as PlayerFragment
        val root = fragment.requireView()
        // La vista del fragmento no ha pasado traversal: layout manual (patrón
        // del repo, ChannelBrowserVerContainTest) — sin bounds 0,0-0,0 el
        // dispatch de teclas no llega al OnKeyListener de la raíz.
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1920, 1080)
        val rew = root.findViewById<ImageView>(R.id.player_rew)
        val play = root.findViewById<ImageView>(R.id.player_playpause)
        val ff = root.findViewById<ImageView>(R.id.player_ff)
        val ext = root.findViewById<ImageView>(R.id.player_external)
        return Triple(fragment, listOfNotNull(rew, play, ff, ext), root)
    }

    @Test
    fun `b dpad alcanza el transporte con zap y multimedia intactos`() {
        val (fragment, transport, root) = boot()
        val (rew, play, ff, ext) = transport
        val overlay = root.findViewById<View>(R.id.player_overlay)
        val title = root.findViewById<TextView>(R.id.player_title)

        // Zona A: la raíz toma el foco y OK muestra el overlay re-focando playpause.
        root.requestFocus()
        assertTrue(root.isFocused)
        down(KeyEvent.KEYCODE_DPAD_CENTER)
        pumpUntil { overlay.visibility == View.VISIBLE && play.hasFocus() }

        // Cadena RIGHT: play → ff → external (nav = focusSearch+requestFocus,
        // el destino lo fija la cadena XML nextFocusRight).
        nav(play, View.FOCUS_RIGHT)
        pumpUntil { ff.hasFocus() }
        nav(ff, View.FOCUS_RIGHT)
        pumpUntil { ext.hasFocus() }

        // Cadena LEFT ×3: external → ff → play → rew.
        nav(ext, View.FOCUS_LEFT)
        pumpUntil { ff.hasFocus() }
        nav(ff, View.FOCUS_LEFT)
        pumpUntil { play.hasFocus() }
        nav(play, View.FOCUS_LEFT)
        pumpUntil { rew.hasFocus() }

        // Zap con foco en transporte: cambia de canal SIN perder el foco.
        down(KeyEvent.KEYCODE_DPAD_DOWN)
        pumpUntil { title.text.toString() == "Dos" && rew.hasFocus() }

        // Teclas multimedia con foco en transporte: el listener compartido las
        // CONSUME y las enruta a playback (togglePlay reintenta la preparación
        // tras error). dispatchKeyEvent devuelve true SOLO si algún handler
        // consumió la tecla (sin listener propio de media keys en Activity).
        // (El spinner Loading→Error es transitorio: no es observable fiable.)
        assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)))

        // BACK (dispatcher) cierra el player.
        activity.onBackPressedDispatcher.onBackPressed()
        pumpUntil {
            activity.supportFragmentManager.findFragmentById(R.id.container) !is PlayerFragment
        }
    }

    /**
     * T-F3 (hueco tester-1, S07): con foco en transporte, LEFT/RIGHT/CENTER
     * NO se consumen (dispatch real por Activity, no focusSearch): la cadena
     * XML mueve el foco y el framework hace el click. El listener ya devuelve
     * false en `else -> false` — si pasa a la primera es verificación.
     */
    @Test
    fun `b2 left right center no se consumen en transporte`() {
        val (_, transport, _) = boot()
        val play = transport[1]
        play.requestFocus()
        pumpUntil { play.hasFocus() }
        assertFalse(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)))
        assertFalse(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
        assertFalse(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)))
    }
}
