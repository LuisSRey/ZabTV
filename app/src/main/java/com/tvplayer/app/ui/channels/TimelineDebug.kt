package com.tvplayer.app.ui.channels

import android.util.Log
import com.tvplayer.app.BuildConfig
import com.tvplayer.app.core.xmltv.GuideWindow

/**
 * Traza diagnóstica del timeline (G2 S5): Log.d solo en debug y solo si el
 * tag está habilitado (`adb shell setprop log.tag.BrowserTimeline DEBUG`).
 * El mensaje lleva SOLO contadores + tvgId + anchor + slots + visibilidades
 * + versión APK — JAMÁS títulos, nombres de canal ni URLs (PII del usuario).
 * Los gates van inyectados para test JVM directo (sin Robolectric).
 */
object TimelineDebug {
    const val TAG = "BrowserTimeline"

    /** Mensaje del lote: contadores + tvgIds + anchor + slots + visibilidades + versión. Puro. */
    fun batchMessage(
        tvgIds: List<String>,
        anchorMillis: Long,
        slotCount: Int,
        visibles: Int,
        epg: Int,
        channels: Int,
        vis: String,
    ): String = "tvg=[${tvgIds.joinToString(",")}] anchor=$anchorMillis slots=$slotCount " +
        "visibles=$visibles epg=$epg canales=$channels vis=[$vis] " +
        "v=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"

    /** Mensaje de retorno vacío: motivo + intento + anchor + slots + versión. Puro. */
    fun emptyMessage(reason: String, attempt: Int, anchorMillis: Long, slotCount: Int): String =
        "vacio motivo=$reason intento=$attempt anchor=$anchorMillis slots=$slotCount " +
            "v=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"

    fun logBatch(
        tvgIds: List<String>,
        window: GuideWindow,
        visibles: Int,
        epg: Int,
        channels: Int,
        vis: String,
        debug: Boolean = BuildConfig.DEBUG,
        isLoggable: () -> Boolean = { Log.isLoggable(TAG, Log.DEBUG) },
        emit: (String) -> Unit = { Log.d(TAG, it) },
    ) {
        if (!debug || !isLoggable()) return
        emit(batchMessage(tvgIds, window.anchorMillis, window.slotCount, visibles, epg, channels, vis))
    }

    fun logEmpty(
        reason: String,
        attempt: Int,
        window: GuideWindow,
        debug: Boolean = BuildConfig.DEBUG,
        isLoggable: () -> Boolean = { Log.isLoggable(TAG, Log.DEBUG) },
        emit: (String) -> Unit = { Log.d(TAG, it) },
    ) {
        if (!debug || !isLoggable()) return
        emit(emptyMessage(reason, attempt, window.anchorMillis, window.slotCount))
    }
}
