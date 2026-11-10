package com.tvplayer.app.data.net

import com.tvplayer.app.core.UrlPolicer
import java.util.Locale

/**
 * Puerta pura del botón "Probar" del proxy (LOW 2): aplica [UrlPolicer] y
 * respeta `allowHttp` para http, igual que el resto de la app. Si no pasa,
 * el llamador muestra el error SIN hacer la petición.
 */
object ProxyProbeGate {

    /** true = se puede probar la URL; false = mostrar error sin pedir red. */
    fun isProbeAllowed(url: String?, allowHttp: Boolean): Boolean {
        if (!UrlPolicer.isAllowedHttpUrl(url)) return false
        val u = url!!.trim().lowercase(Locale.ROOT)
        if (u.startsWith("http://") && !allowHttp) return false
        return true
    }
}
