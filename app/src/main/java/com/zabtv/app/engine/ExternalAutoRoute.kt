package com.zabtv.app.engine

import com.zabtv.app.data.AppContainer
import com.zabtv.app.data.db.SettingsDao

/**
 * Auto-route al reproductor externo en openPlayer (S5). Tabla pura sobre los
 * VETOS de [ExternalGate] (intactos: misma función, sin reimplementar):
 * - INTERNAL: override Nunca, global OFF, o acestream:// (vía propia).
 * - BLOCKED: el usuario lo quería (Siempre o Global ON) pero un veto lo
 *   impide → toast blocked + fallback interno.
 * - EXTERNAL_DIRECT / EXTERNAL_CHOOSER: exportable, directo si el preferido
 *   sigue usable, chooser si no (con toast gone si se desinstaló).
 */
object ExternalAutoRoute {

    enum class Route { INTERNAL, EXTERNAL_DIRECT, EXTERNAL_CHOOSER, BLOCKED }

    fun resolve(
        rawUrl: String,
        allowHttp: Boolean,
        engineProxyFor: String?,
        override: Boolean?,
        global: Boolean,
        preferred: String?,
        preferredUsable: Boolean,
    ): Route {
        if (rawUrl.startsWith("acestream://", ignoreCase = true)) return Route.INTERNAL
        if (!ExternalGate.canExport(rawUrl, allowHttp, engineProxyFor, override, global)) {
            val wanted = override == true || (override == null && global)
            return if (wanted) Route.BLOCKED else Route.INTERNAL
        }
        return if (!preferred.isNullOrBlank() && preferredUsable) {
            Route.EXTERNAL_DIRECT
        } else {
            Route.EXTERNAL_CHOOSER
        }
    }

    /**
     * Política fresca para la ruta: lee SettingsDao (valores vigentes), no la
     * caché de [AppContainer.currentPolicy]. Triple(allowHttp, global, preferido).
     */
    suspend fun freshPolicyForRoute(dao: SettingsDao): Triple<Boolean, Boolean, String?> {
        val allowHttp = dao.get(AppContainer.KEY_ALLOW_HTTP, "false") == "true"
        val global = dao.get(AppContainer.KEY_EXTERNAL_GLOBAL, "false") == "true"
        val preferred = ExternalPlayers.getPreferred(dao, AppContainer.KEY_EXTERNAL_PLAYER_PACKAGE)
        return Triple(allowHttp, global, preferred)
    }
}
