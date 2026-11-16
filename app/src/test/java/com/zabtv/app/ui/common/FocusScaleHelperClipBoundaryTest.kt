package com.zabtv.app.ui.common

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fronteras de clip W1 S1 (TV): [FocusScaleHelper.protectClipBoundary] marca
 * una frontera que el paseo de unclip salta, [FocusScaleHelper.enforceClipBoundary]
 * restaura clips + protege, y el walk abre al resto como antes (halo de foco
 * intacto). Sin tocar callers ni la matemática de escala 1.1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "television")
class FocusScaleHelperClipBoundaryTest {

    private fun activity(): Activity =
        Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun chain(): Triple<Activity, LinearLayout, FrameLayout> {
        val a = activity()
        val root = LinearLayout(a)
        val mid = FrameLayout(a)
        val leaf = View(a)
        root.addView(mid)
        mid.addView(leaf)
        a.setContentView(root)
        return Triple(a, root, mid)
    }

    @Test
    fun `protect marca frontera y el walk la salta`() {
        val (_, root, mid) = chain()
        val leaf = mid.getChildAt(0)
        FocusScaleHelper.protectClipBoundary(mid)
        assertTrue(FocusScaleHelper.isProtected(mid))
        FocusScaleHelper.attach(leaf)
        // Frontera intacta (contención), raíz abierta para el halo de foco.
        assertTrue(mid.clipChildren)
        assertTrue(mid.clipToPadding)
        assertFalse(root.clipChildren)
        assertFalse(root.clipToPadding)
    }

    @Test
    fun `enforce restaura clips y protege`() {
        val (_, root, mid) = chain()
        mid.clipChildren = false
        mid.clipToPadding = false
        FocusScaleHelper.enforceClipBoundary(mid)
        assertTrue(mid.clipChildren)
        assertTrue(mid.clipToPadding)
        assertTrue(FocusScaleHelper.isProtected(mid))
        FocusScaleHelper.attach(mid.getChildAt(0))
        assertTrue(mid.clipChildren)
        assertTrue(mid.clipToPadding)
        assertFalse(root.clipChildren)
        assertFalse(root.clipToPadding)
    }

    @Test
    fun `sin proteccion el walk abre todo como antes`() {
        val (_, root, mid) = chain()
        val leaf = mid.getChildAt(0)
        assertFalse(FocusScaleHelper.isProtected(mid))
        FocusScaleHelper.attach(leaf)
        assertFalse(mid.clipChildren)
        assertFalse(mid.clipToPadding)
        assertFalse(root.clipChildren)
        assertFalse(root.clipToPadding)
    }

    @Test
    fun `attach mantiene foco y listener de escala`() {
        val (a, _, _) = chain()
        val v = View(a)
        (a.findViewById<ViewGroup>(android.R.id.content)).addView(v)
        FocusScaleHelper.attach(v)
        assertTrue(v.isFocusable)
        assertFalse(v.isFocusableInTouchMode)
        assertNotNull(v.onFocusChangeListener)
    }
}
