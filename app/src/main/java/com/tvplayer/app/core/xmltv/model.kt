package com.tvplayer.app.core.xmltv

data class XmltvChannel(val id: String, val displayName: String?)

data class XmltvProgramme(
    val tvgId: String,
    val startMillis: Long,
    val endMillis: Long,
    val title: String,
    val description: String?,
)

/** Canales (paquete chico, ≤ ~10k) + programas como Sequence filtrado por ventana ANTES de materializar. */
data class XmltvResult(val channels: List<XmltvChannel>, val programmes: Sequence<XmltvProgramme>)
