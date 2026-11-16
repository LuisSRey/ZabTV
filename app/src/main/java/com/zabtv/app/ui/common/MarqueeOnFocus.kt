package com.zabtv.app.ui.common

import android.text.TextUtils
import android.view.View
import android.widget.TextView

/**
 * Marquee al foco (S4): reposo END + selected=false ↔ foco MARQUEE +
 * selected=true (repeatLimit 1, maxLines 1). Para el selector (anchor
 * propio) y group_label (anchor = fila); group_count nunca.
 * Encadena el listener previo del anchor (típico [FocusScaleHelper]).
 */
object MarqueeOnFocus {

    /** Una sola pasada al perder/ganar el foco (sin bucle infinito). */
    const val REPEAT_LIMIT = 1

    /** Selector: el propio label recibe el foco. */
    fun attach(label: TextView) = bind(label, label)

    /** Fila de grupo: el marquee del label sigue al foco de la fila. */
    fun bind(label: TextView, anchor: View) {
        label.maxLines = 1
        label.marqueeRepeatLimit = REPEAT_LIMIT
        label.ellipsize = TextUtils.TruncateAt.END
        label.isSelected = false
        val prev = anchor.onFocusChangeListener
        anchor.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            prev?.onFocusChange(v, hasFocus)
            if (hasFocus) {
                label.ellipsize = TextUtils.TruncateAt.MARQUEE
                label.isSelected = true
            } else {
                label.ellipsize = TextUtils.TruncateAt.END
                label.isSelected = false
            }
        }
    }
}
