package com.zabtv.app.engine

import com.zabtv.app.R

/**
 * SECCION-04 (T-P0-4 · D3/A3-03): mapa veto → mensaje accionable (matriz
 * 2.1.3 del DESIGN), compartido por el auto-route (MainActivity.openPlayer)
 * y el botón externo del player (PlayerFragment.openExternal). Un string
 * específico por causa; solo el cleartext es toast largo (instrucciones de
 * dos pasos). El toast genérico player_external_blocked queda SOLO como rama
 * defensiva inalcanzable: ACESTREAM y POLICY_OFF van a INTERNAL, nunca a
 * BLOCKED (ExternalAutoRoute.resolve).
 */
object ExternalVetoUi {

    /** null = sin veto (no debería ocurrir en rutas BLOCKED): defensiva genérica. */
    fun messageRes(veto: ExternalGate.Veto?): Int = when (veto) {
        ExternalGate.Veto.ENGINE_LOOPBACK -> R.string.player_ext_veto_loopback
        ExternalGate.Veto.BAD_SCHEME -> R.string.player_ext_veto_url
        ExternalGate.Veto.USER_INFO -> R.string.player_ext_veto_userinfo
        ExternalGate.Veto.CLEARTEXT -> R.string.player_ext_veto_cleartext
        // Defensiva inalcanzable (ver KDoc).
        null, ExternalGate.Veto.ACESTREAM, ExternalGate.Veto.POLICY_OFF -> R.string.player_external_blocked
    }

    /** Solo el cleartext lleva instrucciones de dos pasos: LENGTH_LONG. */
    fun longToast(veto: ExternalGate.Veto?): Boolean = veto == ExternalGate.Veto.CLEARTEXT
}
