package com.tvplayer.app.ui.common

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup

/**
 * Comportamiento TV del spec (§3/§5): focus visible siempre con scale 1.0→1.1 (+elevación).
 * En dispositivos no-TV (móvil/tablet) degenera en no-op: el touch no necesita focus.
 */
object FocusScaleHelper {

    /**
     * El anti-clip debe correr con la cadena de padres COMPLETA: en
     * onCreateViewHolder la vista aún no tiene padre y el paseo no servía.
     * Se difiere a cuando la vista se engancha a la ventana.
     */
    fun attach(view: View, onFocus: ((View, Boolean) -> Unit)? = null) {
        if (!isTv(view)) return
        if (view.isAttachedToWindow) {
            unclipAncestors(view)
        } else {
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    unclipAncestors(v)
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            })
        }
        view.isFocusable = true
        view.isFocusableInTouchMode = false
        view.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            val target = if (hasFocus) 1.1f else 1.0f
            v.animate().scaleX(target).scaleY(target)
                .translationZ(if (hasFocus) 8f else 0f)
                .setDuration(120)
                .start()
            onFocus?.invoke(v, hasFocus)
        }
    }

    private fun unclipAncestors(view: View) {
        // La escala 1.1 desborda los bordes del padre: sin esto el foco se
        // recorta (típico: lado izquierdo de botones y tiles en Fire TV).
        var parent = view.parent
        while (parent is ViewGroup) {
            parent.clipChildren = false
            parent.clipToPadding = false
            parent = parent.parent
        }
    }

    fun isTv(view: View): Boolean {
        val uiModeType = view.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return uiModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
}
