package com.tvplayer.app.ui.common

import android.app.Activity
import android.view.View
import com.tvplayer.app.R

/**
 * Colapso del sidebar TV (S2): solo en tab browser el shell reposa en
 * iconos (88dp, labels + app_title GONE); el foco en cualquier nav_*
 * expande (200dp VISIBLE) y al salir el foco del sidebar colapsa.
 * contentDescription = etiqueta para a11y sin labels visibles.
 * No toca selección (activated/colores), chrome (visibilidad del sidebar)
 * ni back stack: solo ancho + visibilidades de labels/título.
 */
object SidebarCollapseController {

    /** Ancho expandido = sidebar S1. */
    const val EXPANDED_DP = 200

    /** Ancho colapsado solo-iconos. */
    const val COLLAPSED_DP = 88

    private val NAV_IDS = listOf(
        R.id.nav_home,
        R.id.nav_browse,
        R.id.nav_search,
        R.id.nav_lists,
        R.id.nav_settings,
    )

    private fun labelId(navId: Int): Int = when (navId) {
        R.id.nav_home -> R.id.nav_home_label
        R.id.nav_browse -> R.id.nav_browse_label
        R.id.nav_search -> R.id.nav_search_label
        R.id.nav_lists -> R.id.nav_lists_label
        else -> R.id.nav_settings_label
    }

    private fun labelRes(navId: Int): Int = when (navId) {
        R.id.nav_home -> R.string.nav_home
        R.id.nav_browse -> R.string.browser_title
        R.id.nav_search -> R.string.search_title
        R.id.nav_lists -> R.string.lists_title
        else -> R.string.settings_title
    }

    /** Colapsa solo con la tab browser visible. Puro para test directo. */
    fun shouldCollapse(currentTag: String?, browserTag: String): Boolean =
        currentTag == browserTag

    /**
     * Aplica el estado: ancho + labels/título GONE/VISIBLE. Nunca toca la
     * visibilidad del sidebar (la dueña es syncShell) ni la selección.
     */
    fun apply(sidebar: View, appTitle: View?, labels: List<View>, collapsed: Boolean) {
        val px = (
            (if (collapsed) COLLAPSED_DP else EXPANDED_DP) *
                sidebar.resources.displayMetrics.density
            ).toInt()
        if (sidebar.layoutParams.width != px) {
            sidebar.layoutParams = sidebar.layoutParams.apply { width = px }
        }
        val vis = if (collapsed) View.GONE else View.VISIBLE
        labels.forEach { if (it.visibility != vis) it.visibility = vis }
        if (appTitle != null && appTitle.visibility != vis) appTitle.visibility = vis
    }

    private fun labelsOf(activity: Activity): List<View> =
        NAV_IDS.mapNotNull { activity.findViewById<View>(labelId(it)) }

    /**
     * Estado según la tab vigente. No-op sin sidebar (móvil/tablet usan la
     * barra inferior y no colapsan).
     */
    fun refresh(activity: Activity, currentTag: String?, browserTag: String) {
        val sidebar = activity.findViewById<View>(R.id.sidebar) ?: return
        apply(sidebar, activity.findViewById(R.id.app_title), labelsOf(activity), shouldCollapse(currentTag, browserTag))
    }

    /**
     * Cablea foco + a11y (idempotente: encadena el listener previo, típico
     * el de [FocusScaleHelper], sin quitar la escala). [tags] lee la tab
     * vigente para que el foco solo colapse en browser.
     */
    fun attach(activity: Activity, tags: () -> String?, browserTag: String) {
        val sidebar = activity.findViewById<View>(R.id.sidebar) ?: return
        for (navId in NAV_IDS) {
            val row = activity.findViewById<View>(navId) ?: continue
            row.contentDescription = activity.getString(labelRes(navId))
            val prev = row.onFocusChangeListener
            row.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                prev?.onFocusChange(v, hasFocus)
                if (!shouldCollapse(tags(), browserTag)) return@OnFocusChangeListener
                if (hasFocus) {
                    apply(sidebar, activity.findViewById(R.id.app_title), labelsOf(activity), false)
                } else {
                    // El foco va en tránsito: decide cuando asiente fuera/dentro.
                    row.post {
                        if (shouldCollapse(tags(), browserTag) && !sidebar.hasFocus()) {
                            apply(sidebar, activity.findViewById(R.id.app_title), labelsOf(activity), true)
                        }
                    }
                }
            }
        }
    }
}
