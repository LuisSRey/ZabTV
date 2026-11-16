package com.zabtv.app.ui.player

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerImmersiveTest {

    @Test
    fun movilHorizontal_esCandidato() {
        assertTrue(PlayerFragment.shouldUseImmersive(false, Configuration.ORIENTATION_LANDSCAPE))
    }

    @Test
    fun movilVertical_noEsCandidato() {
        assertFalse(PlayerFragment.shouldUseImmersive(false, Configuration.ORIENTATION_PORTRAIT))
    }

    @Test
    fun tvHorizontal_noEsCandidato() {
        assertFalse(PlayerFragment.shouldUseImmersive(true, Configuration.ORIENTATION_LANDSCAPE))
    }

    @Test
    fun tvVertical_noEsCandidato() {
        assertFalse(PlayerFragment.shouldUseImmersive(true, Configuration.ORIENTATION_PORTRAIT))
    }
}
