package com.tvplayer.app.engine

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

    /** Paquetes conocidos de la app AceStream (motor). */
    /** Paquetes del motor (el usuario prefiere AceServe). */
    private val ENGINE_PACKAGES = listOf(
        "org.free.aceserve",
        "org.acestream.media",
        "org.acestream.engine",
    )

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

    /** Intent para abrir la app del motor (busca por nombre, no por id fijo). */
    fun launchIntent(context: Context): Intent? {
        val pm: PackageManager = context.packageManager
        val pkgs = try {
            pm.getInstalledPackages(0).map { it.packageName }
        } catch (_: Exception) {
            emptyList<String>()
        }
        val candidates = (pkgs.filter { it.contains("acestream", ignoreCase = true) } + ENGINE_PACKAGES).distinct()
        for (pkg in candidates) {
            try {
                val intent = pm.getLaunchIntentForPackage(pkg) ?: continue
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return intent
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    /** Intent VIEW del enlace: lo resuelve la app AceStream aunque no exponga launcher. */
    fun viewIntent(aceStreamUrl: String): android.content.Intent =
        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(aceStreamUrl))

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
