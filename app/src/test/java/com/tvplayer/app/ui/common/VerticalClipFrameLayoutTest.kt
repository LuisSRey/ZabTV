package com.tvplayer.app.ui.common

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S1 FRENTE B: [VerticalClipFrameLayout] contiene SOLO en vertical (clip de
 * canvas 0..height, holgura lateral 10 000 px para el halo 1.1) y es inmune al
 * unclip de flags de [FocusScaleHelper] (flags ≠ clip de canvas). El bitmap de
 * la sonda deja holgura extra a propósito: sin el clip del wrap la sonda leería
 * ±(slop+holgura), no ±slop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "television")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VerticalClipFrameLayoutTest {

    /**
     * Sonda: graba el clip efectivo del canvas dentro del draw. El canvas llega
     * trasladado por (left,top) del hijo, así que se deshace el offset para
     * expresarlo en coordenadas del wrap (tal como pide el diseño).
     */
    private class ProbeView(context: Context) : View(context) {
        var clipInWrap: Rect? = null

        override fun onDraw(canvas: Canvas) {
            val bounds = Rect()
            canvas.getClipBounds(bounds)
            bounds.offset(left, top)
            clipInWrap = bounds
            super.onDraw(canvas)
        }
    }

    private companion object {
        const val SIZE = 120

        // La holgura del lienzo es MAYOR que el slop del wrap: si el wrap no
        // recorta, la sonda ve ±(slop+holgura) y el test cae.
        const val SLACK_X = 10_200f
        const val SLACK_Y = 110f

        const val CHILD_PAD = 200
        const val CHILD_TOP_PAD = 150
    }

    private fun exactly(size: Int): Int =
        View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun newCanvas(): Pair<Bitmap, Canvas> {
        val bitmap = Bitmap.createBitmap(
            (SIZE + 2 * SLACK_X).toInt(),
            (SIZE + 2 * SLACK_Y).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        return bitmap to Canvas(bitmap)
    }

    private fun drawInto(wrap: View, canvas: Canvas) {
        val restore = canvas.save()
        canvas.translate(SLACK_X, SLACK_Y)
        wrap.draw(canvas)
        canvas.restoreToCount(restore)
    }

    private fun wrapWithProbe(context: Context): Pair<VerticalClipFrameLayout, ProbeView> {
        val wrap = VerticalClipFrameLayout(context)
        val probe = ProbeView(context)
        wrap.addView(probe)
        // Matriz de producción del wrap TV: sin clips de flags (el clip es de canvas).
        wrap.clipChildren = false
        wrap.clipToPadding = false
        wrap.measure(exactly(SIZE), exactly(SIZE))
        wrap.layout(0, 0, SIZE, SIZE)
        // Hijo sobredimensionado en las cuatro direcciones: solo el clip del
        // wrap puede devolver 0..height / ±slop.
        probe.layout(-CHILD_PAD, -CHILD_TOP_PAD, SIZE + CHILD_PAD, SIZE + CHILD_TOP_PAD)
        return wrap to probe
    }

    @Test
    fun `clip canvas 0height y slop lateral 10000`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (wrap, probe) = wrapWithProbe(context)
        val (bitmap, canvas) = newCanvas()
        drawInto(wrap, canvas)
        val clip = probe.clipInWrap
        assertNotNull(clip)
        assertEquals(0, clip!!.top)
        assertEquals(SIZE, clip.bottom)
        assertEquals(-VerticalClipFrameLayout.CLIP_SIDE_SLOP_PX.toInt(), clip.left)
        assertEquals(SIZE + VerticalClipFrameLayout.CLIP_SIDE_SLOP_PX.toInt(), clip.right)
        bitmap.recycle()
    }

    @Test
    fun `slop lateral cubre el halo 1_1`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val density = context.resources.displayMetrics.density
        // 1.1 de escala sobre una fila de ~896dp: desborde real 44.8dp/lado.
        assertTrue(VerticalClipFrameLayout.CLIP_SIDE_SLOP_PX >= 64f * density)
    }

    @Test
    fun `inmune a unclipAncestors flags no tocan el clip de canvas`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val (wrap, probe) = wrapWithProbe(activity)
        activity.setContentView(wrap)
        wrap.layout(0, 0, SIZE, SIZE)
        probe.layout(-CHILD_PAD, -CHILD_TOP_PAD, SIZE + CHILD_PAD, SIZE + CHILD_TOP_PAD)
        // Frontera de flags CERRADA de partida: el walk de attach debe abrirla.
        wrap.clipChildren = true
        wrap.clipToPadding = true

        FocusScaleHelper.attach(probe)

        // El walk tocó flags (wrap y raíz abiertas) y NO marcó frontera...
        assertTrue(probe.isFocusable)
        assertFalse(wrap.clipChildren)
        assertFalse(wrap.clipToPadding)
        assertFalse(FocusScaleHelper.isProtected(wrap))
        val root = activity.findViewById<View>(android.R.id.content) as android.view.ViewGroup
        assertFalse(root.clipChildren)

        // ...pero el clip del canvas sigue siendo el del wrap.
        val (bitmap, canvas) = newCanvas()
        drawInto(wrap, canvas)
        val clip = probe.clipInWrap
        assertNotNull(clip)
        assertEquals(0, clip!!.top)
        assertEquals(SIZE, clip.bottom)
        assertEquals(-VerticalClipFrameLayout.CLIP_SIDE_SLOP_PX.toInt(), clip.left)
        assertEquals(SIZE + VerticalClipFrameLayout.CLIP_SIDE_SLOP_PX.toInt(), clip.right)
        bitmap.recycle()
    }
}
