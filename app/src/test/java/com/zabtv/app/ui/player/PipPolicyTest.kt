package com.zabtv.app.ui.player

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S3: política PiP pura — opt-in móvil con feature; TV y SDK<26 vetados; nunca auto-enter. */
class PipPolicyTest {

    @Test
    fun `movil con feature y SDK26+ permite`() {
        assertTrue(PlayerFragment.pipAllowed(26, true, false))
        assertTrue(PlayerFragment.pipAllowed(34, true, false))
    }

    @Test
    fun `SDK menor de 26 veta`() {
        assertFalse(PlayerFragment.pipAllowed(25, true, false))
        assertFalse(PlayerFragment.pipAllowed(23, true, false))
    }

    @Test
    fun `sin feature veta`() {
        assertFalse(PlayerFragment.pipAllowed(34, false, false))
    }

    @Test
    fun `TV veta aunque haya feature`() {
        assertFalse(PlayerFragment.pipAllowed(34, true, true))
    }

    @Test
    fun `en PiP el overlay se oculta, fuera no`() {
        assertTrue(PlayerFragment.shouldHideOverlayInPip(true))
        assertFalse(PlayerFragment.shouldHideOverlayInPip(false))
    }

    @Test
    fun `visibilidad del boton sigue a la politica`() {
        assertEquals(View.VISIBLE, PlayerFragment.pipButtonVisibility(true))
        assertEquals(View.GONE, PlayerFragment.pipButtonVisibility(false))
    }
}
