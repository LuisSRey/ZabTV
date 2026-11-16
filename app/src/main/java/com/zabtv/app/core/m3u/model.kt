package com.zabtv.app.core.m3u

/** Header de la lista M3U (solo datos útiles v0.1). */
data class M3uHeader(val epgUrl: String?)

data class M3uChannel(
    val tvgId: String?,
    val tvgName: String?,
    val logoUrl: String?,
    val groupTitle: String?,
    /** Nombre tras la ÚLTIMA coma del EXTINF (spec: listas reales usan comas dentro del nombre). */
    val name: String,
    val url: String,
    val rawAttrs: Map<String, String>,
)

/** Streaming: [channels] es una Sequence de pull; cerrar el Reader corta el parse. */
data class M3uDocument(val header: M3uHeader?, val channels: Sequence<M3uChannel>)
