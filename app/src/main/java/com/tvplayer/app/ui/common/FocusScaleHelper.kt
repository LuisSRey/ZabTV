package com.tvplayer.app.ui.common

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup

/**
 * Comportamiento TV del spec (§3/§5): focus visible siempre con scale 1.0→1.1 (+elevación).
 * En dispositivos no-TV (móvil/tablet) degenera en no-op: el touch no necesita focus.
 */
object FocusScaleHelper {

    fun attach(view: View) {
        if (!isTv(view)) return
        // La escala 1.1 desborda los bordes del padre: sin esto el foco se
        // recorta (típico: lado izquierdo de botones y tiles en Fire TV).
        var parent = view.parent
        while (parent is ViewGroup) {
            parent.clipChildren = false
            parent.clipToPadding = false
            parent = parent.parent
        }
        view.isFocusable = true
        view.isFocusableInTouchMode = false
        view.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            val target = if (hasFocus) 1.1f else 1.0f
            v.animate().scaleX(target).scaleY(target)
                .translationZ(if (hasFocus) 8f else 0f)
                .setDuration(120)
                .start()
        }
    }

    fun isTv(view: View): Boolean {
        val uiModeType = view.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return uiModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
}
