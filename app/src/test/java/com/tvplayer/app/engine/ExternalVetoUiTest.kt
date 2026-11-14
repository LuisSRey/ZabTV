package com.tvplayer.app.engine

import com.tvplayer.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SECCION-04 (T-P0-4 · D3/A3-03): mapa veto → mensaje accionable (matriz
 * 2.1.3 del DESIGN). Un string específico por causa; el cleartext es el único
 * de larga duración (instrucciones de dos pasos). El toast genérico
 * player_external_blocked queda SOLO como rama defensiva inalcanzable
 * (ACESTREAM y POLICY_OFF nunca producen BLOCKED: van a INTERNAL).
 */
class ExternalVetoUiTest {

    @Test
    fun `loopback mensaje especifico`() {
        assertEquals(
            R.string.player_ext_veto_loopback,
            ExternalVetoUi.messageRes(ExternalGate.Veto.ENGINE_LOOPBACK),
        )
    }

    @Test
    fun `bad scheme mensaje url`() {
        assertEquals(
            R.string.player_ext_veto_url,
            ExternalVetoUi.messageRes(ExternalGate.Veto.BAD_SCHEME),
        )
    }

    @Test
    fun `user info mensaje especifico`() {
        assertEquals(
            R.string.player_ext_veto_userinfo,
            ExternalVetoUi.messageRes(ExternalGate.Veto.USER_INFO),
        )
    }

    @Test
    fun `cleartext mensaje especifico`() {
        assertEquals(
            R.string.player_ext_veto_cleartext,
            ExternalVetoUi.messageRes(ExternalGate.Veto.CLEARTEXT),
        )
    }

    @Test
    fun `cleartext es el unico toast largo`() {
        assertTrue(ExternalVetoUi.longToast(ExternalGate.Veto.CLEARTEXT))
        assertFalse(ExternalVetoUi.longToast(ExternalGate.Veto.ENGINE_LOOPBACK))
        assertFalse(ExternalVetoUi.longToast(ExternalGate.Veto.BAD_SCHEME))
        assertFalse(ExternalVetoUi.longToast(ExternalGate.Veto.USER_INFO))
    }

    @Test
    fun `rama defensiva inalcanzable usa el generico`() {
        // ACESTREAM → INTERNAL y POLICY_OFF → INTERNAL: jamás llegan a BLOCKED.
        assertEquals(
            R.string.player_external_blocked,
            ExternalVetoUi.messageRes(ExternalGate.Veto.ACESTREAM),
        )
        assertEquals(
            R.string.player_external_blocked,
            ExternalVetoUi.messageRes(ExternalGate.Veto.POLICY_OFF),
        )
        // null (sin veto) tampoco ocurre en BLOCKED: defensiva.
        assertEquals(
            R.string.player_external_blocked,
            ExternalVetoUi.messageRes(null),
        )
    }
}
