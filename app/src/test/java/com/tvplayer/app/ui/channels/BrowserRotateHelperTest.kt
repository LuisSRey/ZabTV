package com.tvplayer.app.ui.channels

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2: decisión y snapshot de rotación del browser, puros JVM (sin
 * Robolectric): heroMode / needsReinflate / snapshot. TDD RED.
 */
class BrowserRotateHelperTest {

    @Test
    fun `heroMode colapsado y expandido`() {
        assertEquals(BrowserRotateHelper.HeroMode.COLLAPSED, BrowserRotateHelper.heroMode(true))
        assertEquals(BrowserRotateHelper.HeroMode.EXPANDED, BrowserRotateHelper.heroMode(false))
    }

    @Test
    fun `needsReinflate solo movil con giro real`() {
        // Móvil con giro: reinfla (cambia layout-land <-> portrait).
        assertTrue(
            BrowserRotateHelper.needsReinflate(
                false, Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE,
            ),
        )
        assertTrue(
            BrowserRotateHelper.needsReinflate(
                false, Configuration.ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_PORTRAIT,
            ),
        )
        // Misma orientación: managers + repaint bastan.
        assertFalse(
            BrowserRotateHelper.needsReinflate(
                false, Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT,
            ),
        )
        assertFalse(
            BrowserRotateHelper.needsReinflate(
                false, Configuration.ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE,
            ),
        )
        // TV nunca (escudo land-television): solo managers + repaint.
        assertFalse(
            BrowserRotateHelper.needsReinflate(
                true, Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE,
            ),
        )
        assertFalse(
            BrowserRotateHelper.needsReinflate(
                true, Configuration.ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_PORTRAIT,
            ),
        )
        // Sin orientación definida no hay giro que atender.
        assertFalse(
            BrowserRotateHelper.needsReinflate(
                false, Configuration.ORIENTATION_UNDEFINED, Configuration.ORIENTATION_LANDSCAPE,
            ),
        )
        assertFalse(
            BrowserRotateHelper.needsReinflate(
                false, Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_UNDEFINED,
            ),
        )
    }

    @Test
    fun `snapshot captura hero y posiciones`() {
        assertEquals(
            BrowserRotateHelper.Snapshot(true, 4, 2),
            BrowserRotateHelper.snapshot(true, 4, 2),
        )
        assertEquals(
            BrowserRotateHelper.Snapshot(false, 0, 0),
            BrowserRotateHelper.snapshot(false, 0, 0),
        )
    }

    @Test
    fun `snapshot sanea posiciones negativas`() {
        assertEquals(
            BrowserRotateHelper.Snapshot(false, 0, 0),
            BrowserRotateHelper.snapshot(false, -1, -5),
        )
    }
}
