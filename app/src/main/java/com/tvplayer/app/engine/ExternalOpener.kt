package com.tvplayer.app.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.tvplayer.app.core.UrlPolicer

/**
 * Reproductor externo (S4, opt-in). La política efectiva por canal es
 * `override ?: global`: override null = Global, true = Siempre, false = Nunca.
 *
 * VETOS de [ExternalGate.canExport] (puros, testeables sin Android):
 * acestream:// (tiene vía propia), loopback-ace proxificado (el externo no
 * alcanza al proxy local), esquema no-http(s), http con cleartext OFF, y
 * `!(override ?: global)`.
 *
 * ADVERTENCIA (también en UI): el reproductor externo NO hereda el proxy ni
 * el DoH de TVPlayer; sale con la red y el DNS del sistema.
 */
object ExternalGate {

    /**
     * CAUSA del veto (SECCION-01 · D3/A3-03): misma precedencia que
     * [canExport], solo clasifica para que la UX muestre el mensaje
     * accionable específico. El enrutado (ExternalAutoRoute.resolve) NO
     * cambia de semántica: esto es la capa de diagnóstico sobre los vetos.
     */
    enum class Veto { ACESTREAM, ENGINE_LOOPBACK, BAD_SCHEME, USER_INFO, CLEARTEXT, POLICY_OFF }

    /**
     * @param engineProxyFor resultado de `AceStreamEngine.engineProxyFor(url)` o null.
     * @param override override del canal: null = Global, true = Siempre, false = Nunca.
     */
    fun canExport(
        rawUrl: String,
        allowHttp: Boolean,
        engineProxyFor: String?,
        override: Boolean?,
        global: Boolean,
    ): Boolean {
        if (rawUrl.startsWith("acestream://", ignoreCase = true)) return false
        if (engineProxyFor != null) return false
        if (!UrlPolicer.isAllowedHttpUrl(rawUrl)) return false
        if (rawUrl.startsWith("http://", ignoreCase = true) && !allowHttp) return false
        return effectiveAllow(override, global)
    }

    /**
     * Clasifica la CAUSA del veto (null = sin veto, exportable). Primer match
     * gana, mismo orden que [canExport]: acestream antes que todo (vía
     * propia), loopback antes de la inspección de URL (es http válido),
     * BAD_SCHEME/USER_INFO descomponen `!isAllowedHttpUrl`, CLEARTEXT y
     * POLICY_OFF al final.
     */
    fun vetoOf(
        rawUrl: String,
        allowHttp: Boolean,
        engineProxyFor: String?,
        override: Boolean?,
        global: Boolean,
    ): Veto? {
        if (rawUrl.startsWith("acestream://", ignoreCase = true)) return Veto.ACESTREAM
        if (engineProxyFor != null) return Veto.ENGINE_LOOPBACK
        if (!UrlPolicer.isAllowedHttpUrl(rawUrl)) {
            return if (UrlPolicer.hasUserInfo(rawUrl)) Veto.USER_INFO else Veto.BAD_SCHEME
        }
        if (rawUrl.startsWith("http://", ignoreCase = true) && !allowHttp) return Veto.CLEARTEXT
        if (!effectiveAllow(override, global)) return Veto.POLICY_OFF
        return null
    }

    /** Resolución Global/Siempre/Nunca. */
    fun effectiveAllow(override: Boolean?, global: Boolean): Boolean = override ?: global
}

/** Intents VIEW + resolución (necesita PackageManager; el gate puro está arriba). */
object ExternalOpener {

    fun buildIntent(rawUrl: String, mimeType: String?): Intent {
        val uri = Uri.parse(rawUrl)
        return Intent(Intent.ACTION_VIEW, uri).apply {
            if (mimeType != null) setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun canResolve(pm: PackageManager, intent: Intent): Boolean =
        pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()

    /**
     * Abre con selector del sistema. Devuelve false si no hay app que lo
     * resuelva o si el start falla (nunca lanza).
     */
    fun openWithChooser(context: Context, intent: Intent): Boolean = runCatching {
        val chooser = Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!canResolve(context.packageManager, intent)) return false
        context.startActivity(chooser)
        true
    }.getOrDefault(false)

    /**
     * Abre directo en el paquete preferido (intent explícito, sin chooser).
     * Devuelve false si el paquete ya no resuelve (el caller cae al chooser).
     */
    fun openDirect(context: Context, intent: Intent, packageName: String): Boolean = runCatching {
        val explicit = Intent(intent).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!ExternalPlayers.isPreferredUsable(context.packageManager, packageName, intent)) return false
        context.startActivity(explicit)
        true
    }.getOrDefault(false)

    /**
     * Vía externa completa para un canal:
     * - `acestream://` → integración oficial (Ace Player, sin proxy): [AceStreamEngine.startContentIntent].
     * - http(s) permitido por [ExternalGate] → preferido directo si
     *   [preferredPackage] sigue instalado, si no chooser como antes.
     * Devuelve false si está vetado o no hay receptor.
     */
    fun openChannel(
        context: Context,
        rawUrl: String,
        source: PlaybackSource?,
        allowHttp: Boolean,
        engineProxyFor: String?,
        override: Boolean?,
        global: Boolean,
        preferredPackage: String? = null,
    ): Boolean {
        if (rawUrl.startsWith("acestream://", ignoreCase = true)) {
            return runCatching {
                context.startActivity(AceStreamEngine.startContentIntent(rawUrl))
                true
            }.getOrDefault(false)
        }
        if (!ExternalGate.canExport(rawUrl, allowHttp, engineProxyFor, override, global)) return false
        val intent = buildIntent(rawUrl, source?.mimeType())
        if (!preferredPackage.isNullOrBlank() &&
            ExternalPlayers.isPreferredUsable(context.packageManager, preferredPackage, intent)
        ) {
            return openDirect(context, intent, preferredPackage)
        }
        return openWithChooser(context, intent)
    }
}
