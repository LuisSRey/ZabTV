package com.zabtv.app.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Motor AceStream local (app AceStream del dispositivo, proxy 127.0.0.1:6878).
 * Antes de reproducir un enlace acestream:// se comprueba que responde; si no,
 * se intenta lanzar la app del motor y si ni siquiera está instalada se avisa.
 */
object AceStreamEngine {

    /** Paquetes del motor (el usuario prefiere AceServe, servidor sin pausas). */
    private val ENGINE_PACKAGES = listOf(
        "org.free.aceserve",
        "org.acestream.media",
        "org.acestream.media.atv",
        "org.acestream.core",
        "org.acestream.core.atv",
        "org.acestream.engine",
        "org.acestream.node",
        "org.acestream.node.web",
    )

    /** Acción oficial de integración básica (reproduce en el Ace Player, sin límite de proxy). */
    const val START_CONTENT_ACTION = "org.acestream.action.start_content"

    /** ¿Hay algo escuchando en host:port? (socket corto, sin red real). */
    fun isReachable(host: String, port: Int, timeoutMs: Int = 1500): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Intent para abrir la app del motor. Prueba los paquetes conocidos
     * directamente (getLaunchIntentForPackage ya respeta <queries>) y, como
     * red, cualquier app que resuelva VIEW acestream:// (descubrimiento
     * dinámico, robusto en Android 11+).
     */
    fun launchIntent(context: Context): Intent? {
        val pm: PackageManager = context.packageManager
        for (pkg in ENGINE_PACKAGES) {
            try {
                pm.getLaunchIntentForPackage(pkg)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    return it
                }
            } catch (_: Exception) {
                continue
            }
        }
        // Red: el handler que el sistema resuelva para el esquema acestream://.
        return try {
            val probe = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("acestream://probe"))
            val handlers = pm.queryIntentActivities(probe, 0)
            val pkg = handlers.firstOrNull()?.activityInfo?.packageName ?: return null
            pm.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Hash/content_id de un enlace acestream:// (vacío si no hay nada). */
    fun contentIdOf(aceStreamUrl: String): String =
        aceStreamUrl.trim().substringAfter("://", "").trim()

    /** URI de datos de la integración básica oficial (start_content). */
    fun startContentDataUri(contentId: String): String =
        "acestream:?content_id=${contentId.trim()}"

    /**
     * Intent de integración básica: reproduce el contenido en el propio
     * Ace Player (app oficial). No usa el proxy local, así que no le afecta
     * el límite de 5 min de la opción "Proxy Server" en reproductores externos.
     */
    fun startContentIntent(aceStreamUrl: String, pkg: String? = null): Intent {
        val hash = contentIdOf(aceStreamUrl)
        val intent = Intent(START_CONTENT_ACTION, android.net.Uri.parse(startContentDataUri(hash)))
        if (pkg != null) intent.setPackage(pkg)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    /** Intent VIEW del enlace: lo resuelve la app AceStream aunque no exponga launcher. */
    fun viewIntent(aceStreamUrl: String): android.content.Intent =
        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(aceStreamUrl))

    /**
     * Proxy local a chequear antes de reproducir (null = no necesita motor).
     * Vale para `acestream://hash` (proxy por defecto) y para URLs ya
     * proxificadas (`http://127.0.0.1:6878/ace/getstream?id=…`, forma que
     * traen algunas listas): en ambas el motor caído debe mostrar el diálogo
     * en vez de un error de red en la guía. Un loopback que no parezca proxy
     * ace (otro servidor local) o cualquier remoto devuelven null.
     */
    fun engineProxyFor(url: String, defaultProxy: String = "127.0.0.1:6878"): String? {
        val raw = url.trim()
        if (raw.startsWith("acestream://", ignoreCase = true)) return defaultProxy
        return try {
            val uri = java.net.URI(raw)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host?.lowercase() ?: return null
            val loopback = host == "127.0.0.1" || host == "localhost" ||
                host == "::1" || host == "[::1]" || host == "0:0:0:0:0:0:0:1"
            if (!loopback || !uri.path.orEmpty().startsWith("/ace/")) return null
            val h = if (host.startsWith("[")) "127.0.0.1" else uri.host
            if (uri.port > 0) "$h:${uri.port}" else "$h:6878"
        } catch (_: Exception) {
            null
        }
    }

    /**
     * URL acestream:// para el fallback VIEW del diálogo ("Abrir motor"):
     * con una pre-proxificada se reconstruye desde `?id=` para que la abra
     * el motor y no el navegador.
     */
    fun aceStreamUrlForView(url: String): String {
        val raw = url.trim()
        if (raw.startsWith("acestream://", ignoreCase = true)) return raw
        return try {
            val id = java.net.URI(raw).query?.split('&')
                ?.firstOrNull { it.startsWith("id=") }
                ?.removePrefix("id=")?.trim().orEmpty()
            if (id.isNotEmpty()) "acestream://$id" else raw
        } catch (_: Exception) {
            raw
        }
    }

    fun splitHostPort(proxy: String, defaultPort: Int = 6878): Pair<String, Int> {
        val clean = proxy.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        val parts = clean.split(':')
        if (parts.size == 2) {
            val port = parts[1].toIntOrNull() ?: defaultPort
            if (parts[0].isNotBlank()) return parts[0] to port
        }
        return if (clean.isNotBlank() && !clean.contains('/')) clean to defaultPort else "127.0.0.1" to defaultPort
    }
}
