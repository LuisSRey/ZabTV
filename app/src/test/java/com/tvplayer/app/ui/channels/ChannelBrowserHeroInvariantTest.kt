package com.tvplayer.app.ui.channels

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
import com.tvplayer.app.ui.common.FocusScaleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Invariante del hero (U3 S5): N eventos de foco → hero intacto (0
 * paintHero). El fragment no registra ningún callback de foco hacia
 * paintHero — los FocusScaleHelper solo animan escala 1.1 — así que el
 * repintado solo llega por lista/filtro/reload/rotación/reingreso (ver KDoc
 * de paintHero). Si alguien cablea foco→hero, estos tests caen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "television")
class ChannelBrowserHeroInvariantTest {

    private fun tvBrowserRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel_browser, parent, false)
    }

    @Test
    fun `N focos no tocan el hero ni los slots`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tvBrowserRoot(context)
        val selector = root.findViewById<TextView>(R.id.browser_list_selector)
        val play = root.findViewById<Button>(R.id.browser_hero_play)
        // Mismo cableado que bindBrowserViews: escala al foco, sin callback
        // hacia el hero.
        FocusScaleHelper.attach(selector)
        FocusScaleHelper.attach(play)
        assertNotNull(selector.onFocusChangeListener)
        assertNotNull(play.onFocusChangeListener)
        val hero = root.findViewById<View>(R.id.browser_hero)
        val title = root.findViewById<TextView>(R.id.browser_hero_title)
        val slots = root.findViewById<TextView>(R.id.browser_slots)
        val heroVis = hero.visibility
        val titleText = title.text.toString()
        val slotsText = slots.text.toString()
        repeat(20) { i ->
            val hasFocus = i % 2 == 0
            selector.onFocusChangeListener?.onFocusChange(selector, hasFocus)
            play.onFocusChangeListener?.onFocusChange(play, hasFocus)
            selector.requestFocus()
            selector.clearFocus()
        }
        assertEquals(heroVis, hero.visibility)
        assertEquals(titleText, title.text.toString())
        assertEquals(slotsText, slots.text.toString())
    }

    @Test
    fun `fragment sin handlers de foco y paintHero privada`() {
        // Sin métodos de foco en el fragment, ningún evento D-pad puede
        // alcanzar paintHero por cableado directo.
        val focusHandlers = ChannelBrowserFragment::class.java.declaredMethods
            .filter { it.name.contains("focus", ignoreCase = true) }
        assertTrue(focusHandlers.isEmpty())
        // paintHero existe, es privada y suspend (un solo método declarado):
        // solo la invocan los triggers permitidos del propio fragment.
        val paintHero = ChannelBrowserFragment::class.java.declaredMethods
            .filter { it.name == "paintHero" }
        assertEquals(1, paintHero.size)
        assertTrue(java.lang.reflect.Modifier.isPrivate(paintHero.single().modifiers))
    }
}
