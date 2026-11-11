package com.tvplayer.app.ui.channels

/**
 * Decisión y snapshot de rotación del browser (S2). Puro Kotlin sin Android:
 * el fragment aporta isTv, orientaciones (ints de `Configuration`) y
 * posiciones visibles; aquí solo la decisión y el snapshot saneado.
 */
object BrowserRotateHelper {

    /** Modo del hero land (S1): colapsado muestra solo la cabecera. */
    enum class HeroMode { EXPANDED, COLLAPSED }

    /** Estado a restaurar tras reinflar: hero + primeras posiciones visibles + texto del selector. */
    data class Snapshot(
        val heroCollapsed: Boolean,
        val gridPosition: Int,
        val sidebarPosition: Int,
        val selectorText: String = "",
    )

    fun heroMode(collapsed: Boolean): HeroMode =
        if (collapsed) HeroMode.COLLAPSED else HeroMode.EXPANDED

    /**
     * Reinflar SOLO en móvil con giro real (layout-land y portrait difieren).
     * TV nunca (escudo land-television) ni cambios sin giro (multiventana,
     * teclado): ahí managers + repaint bastan. Sin orientación definida (0 =
     * `ORIENTATION_UNDEFINED`) no hay giro que atender.
     */
    fun needsReinflate(isTv: Boolean, oldOrientation: Int, newOrientation: Int): Boolean =
        !isTv && oldOrientation != newOrientation &&
            oldOrientation != ORIENTATION_UNDEFINED && newOrientation != ORIENTATION_UNDEFINED

    /** Snapshot saneado (posiciones negativas a 0). */
    fun snapshot(
        heroCollapsed: Boolean,
        gridPosition: Int,
        sidebarPosition: Int,
        selectorText: String = "",
    ): Snapshot =
        Snapshot(heroCollapsed, gridPosition.coerceAtLeast(0), sidebarPosition.coerceAtLeast(0), selectorText)

    /** Espejo de `Configuration.ORIENTATION_UNDEFINED` sin importar Android. */
    const val ORIENTATION_UNDEFINED = 0
}
