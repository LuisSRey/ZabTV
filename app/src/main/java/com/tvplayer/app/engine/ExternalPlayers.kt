package com.tvplayer.app.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import com.tvplayer.app.data.db.SettingsDao

/**
 * Reproductor externo preferido (Ajustes): lista los reproductores de vídeo
 * instalados para que el usuario elija uno o "Preguntar siempre".
 *
 * Detección: PackageManager.queryIntentActivities con ACTION_VIEW, tipo
 * de vídeo y data http/https (los handlers reales). Requiere el bloque
 * <queries> con mimeType de vídeo en el manifest (Android 11+).
 */
data class ExternalPlayerInfo(
    val packageName: String,
    val label: String,
    /** Solo UI (icono del launcher); nunca se persiste ni se compara. */
    val icon: Drawable? = null,
)

object ExternalPlayers {

    /** Intent sonda: reproduce lo que un canal http(s) pediría al sistema. */
    fun probeIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse("https://example.com/video.mp4"), "video/*")
        addCategory(Intent.CATEGORY_DEFAULT)
    }

    /** Apps instaladas que resuelven vídeo, sin duplicados ni la propia app, ordenadas. */
    fun listVideoPlayers(context: Context): List<ExternalPlayerInfo> {
        val pm = context.packageManager
        val found = runCatching {
            pm.queryIntentActivities(probeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList())
        return sortedUnique(
            found.map {
                ExternalPlayerInfo(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(pm)?.toString().orEmpty().ifBlank { it.activityInfo.packageName },
                    icon = runCatching { it.loadIcon(pm) }.getOrNull(),
                )
            },
            ownPackage = context.packageName,
        )
    }

    /**
     * Puro y testeable: quita duplicados (por paquete) y la propia app,
     * ignora paquetes en blanco y ordena por etiqueta (insensible a
     * mayúsculas). La primera etiqueta gana en duplicados.
     */
    fun sortedUnique(entries: List<ExternalPlayerInfo>, ownPackage: String): List<ExternalPlayerInfo> =
        entries.filter { it.packageName.isNotBlank() && it.packageName != ownPackage }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    /**
     * true si el paquete preferido sigue instalado Y resuelve el intent
     * real (explícito dirigido a él). false = "Preguntar siempre" o
     * desinstalado → el caller cae al chooser.
     */
    fun isPreferredUsable(pm: PackageManager, packageName: String?, intent: Intent): Boolean {
        if (packageName.isNullOrBlank()) return false
        val explicit = Intent(intent).setPackage(packageName)
        return runCatching {
            pm.queryIntentActivities(explicit, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        }.getOrDefault(false)
    }

    /** null = "Preguntar siempre" (fila "" en SettingsDao). */
    suspend fun getPreferred(dao: SettingsDao, key: String): String? =
        dao.get(key, "")?.ifBlank { null }

    suspend fun setPreferred(dao: SettingsDao, key: String, packageName: String?) {
        dao.put(key, packageName.orEmpty())
    }
}
