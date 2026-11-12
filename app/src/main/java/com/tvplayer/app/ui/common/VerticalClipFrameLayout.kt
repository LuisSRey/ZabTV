package com.tvplayer.app.ui.common

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Contenedor con contención SOLO vertical (FRENTE B, S1):
 *
 * - Recorta en el canvas el rango vertical `0..height` del propio wrap, así las
 *   filas escaladas 1.1 no pintan sobre los slots ni sobre el toprow/hero, y no
 *   sangran bajo la safe area 56dp de TV.
 * - Deja una holgura lateral de [CLIP_SIDE_SLOP_PX] px a cada lado: el halo
 *   lateral del foco 1.1 (±44.8dp a densidad 1.5) sigue visible; el recorte es
 *   un clip de canvas, no de flags (inmune a unclipAncestors de
 *   [FocusScaleHelper]: `clipChildren`/`clipToPadding` son otra cosa que el clip
 *   del canvas).
 * - Trade-off aceptado: la sombra Z8 del foco y el glow de overscroll quedan
 *   cortados en el borde vertical del wrap.
 * - No toca flags, padding ni estado de la vista: solo `dispatchDraw`.
 */
class VerticalClipFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    override fun dispatchDraw(canvas: Canvas) {
        val restore = canvas.save()
        canvas.clipRect(
            -CLIP_SIDE_SLOP_PX,
            0f,
            width + CLIP_SIDE_SLOP_PX,
            height.toFloat(),
        )
        super.dispatchDraw(canvas)
        canvas.restoreToCount(restore)
    }

    companion object {
        /** Holgura lateral del clip: el halo 1.1 no se recorta en los lados. */
        internal const val CLIP_SIDE_SLOP_PX = 10_000f
    }
}
