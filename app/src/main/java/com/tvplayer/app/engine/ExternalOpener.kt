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

    /** Tipo genérico de vídeo (probe de Ajustes y chooser/export). */
    private const val VIDEO_WILDCARD = "video/*"

    fun buildIntent(rawUrl: String, mimeType: String?): Intent {
        val uri = Uri.parse(rawUrl)
        return Intent(Intent.ACTION_VIEW, uri).apply {
            if (mimeType != null) setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * SECCION-03 (D2/A3-02): candidatos de export coherentes con la detección
     * (el probe de Ajustes usa el tipo genérico de vídeo). Con MIME exacto
     * (HLS/DASH) → [exacto, genérico] (dedup si el exacto ya es el genérico);
     * sin MIME (Progressive) → [genérico]. El preferido se abre con el PRIMER
     * candidato que él resuelva: cubre a los players "solo-MIME-exacto" y a
     * los "solo-genérico".
     */
    fun exportCandidates(rawUrl: String, mimeType: String?): List<Intent> {
        val exact = if (mimeType == null) VIDEO_WILDCARD else mimeType
        return if (exact == VIDEO_WILDCARD) {
            listOf(buildIntent(rawUrl, VIDEO_WILDCARD))
        } else {
            listOf(buildIntent(rawUrl, exact), buildIntent(rawUrl, VIDEO_WILDCARD))
        }
    }

    /** Chooser de export = genérico de vídeo SIEMPRE (mismo conjunto que el spinner). */
    fun chooserIntent(rawUrl: String): Intent = buildIntent(rawUrl, VIDEO_WILDCARD)

    fun canResolve(pm: PackageManager, intent: Intent): Boolean =
        pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()

    /** true si ALGÚN candidato resuelve implícitamente. */
    fun canResolveAny(pm: PackageManager, intents: List<Intent>): Boolean =
        intents.any { canResolve(pm, it) }

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
     * Chooser sobre CANDIDATOS: el intent base es SIEMPRE el genérico de
     * vídeo (por construcción es el último de [exportCandidates]).
     */
    fun openWithChooser(context: Context, intents: List<Intent>): Boolean {
        val base = intents.lastOrNull { it.type == VIDEO_WILDCARD } ?: intents.firstOrNull()
            ?: return false
        return openWithChooser(context, base)
    }

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
     * Directo sobre CANDIDATOS: usa el PRIMER candidato que el paquete
     * resuelva (exacto antes que genérico); false si no resuelve ninguno.
     */
    fun openDirect(context: Context, intents: List<Intent>, packageName: String): Boolean {
        for (intent in intents) {
            if (ExternalPlayers.isPreferredUsable(context.packageManager, packageName, intent)) {
                return openDirect(context, intent, packageName)
            }
        }
        return false
    }

    /** true si el paquete resuelve ALGÚN candidato (el preferido sigue usable). */
    fun isPreferredUsable(
        pm: PackageManager,
        packageName: String?,
        intents: List<Intent>,
    ): Boolean = packageName != null &&
        !packageName.isBlank() &&
        intents.any { ExternalPlayers.isPreferredUsable(pm, packageName, it) }

    /**
     * Vía externa completa para un canal:
     * - `acestream://` → integración oficial (Ace Player, sin proxy): [AceStreamEngine.startContentIntent].
     * - http(s) permitido por [ExternalGate] → preferido directo si
     *   [preferredPackage] sigue instalado (sobre CANDIDATOS), si no chooser
     *   con el genérico de vídeo como antes.
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
            val intent = AceStreamEngine.startContentIntent(rawUrl)
            // T-F4: sin receptor el startActivity falla; comprobarlo ANTES
            // evita depender de la excepción (determinista en tests y en
            // dispositivo). El filtro que resuelve este implícito trae
            // DEFAULT por construcción (startActivity lo exige), así que el
            // pre-check equivale al éxito del lanzamiento; runCatching queda
            // como red ante carreras (desinstalación entre check y launch).
            if (!canResolve(context.packageManager, intent)) return false
            return runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }
        if (!ExternalGate.canExport(rawUrl, allowHttp, engineProxyFor, override, global)) return false
        val candidates = exportCandidates(rawUrl, source?.mimeType())
        if (!preferredPackage.isNullOrBlank() &&
            isPreferredUsable(context.packageManager, preferredPackage, candidates)
        ) {
            return openDirect(context, candidates, preferredPackage)
        }
        return openWithChooser(context, candidates)
    }
}
