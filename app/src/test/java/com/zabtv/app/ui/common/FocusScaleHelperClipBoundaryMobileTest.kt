package com.zabtv.app.ui.common

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Escudo no-TV de las fronteras de clip (W1 S1): fuera de TV, protect/enforce
 * son no-op y attach sigue sin tocar la vista (móvil intacto).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusScaleHelperClipBoundaryMobileTest {

    @Test
    fun `protect y enforce no-op fuera de TV`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(FocusScaleHelper.isTv(View(context)))
        val v = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        FocusScaleHelper.protectClipBoundary(v)
        assertFalse(FocusScaleHelper.isProtected(v))
        FocusScaleHelper.enforceClipBoundary(v)
        assertFalse(v.clipChildren)
        assertFalse(v.clipToPadding)
        assertFalse(FocusScaleHelper.isProtected(v))
    }

    @Test
    fun `attach no-op fuera de TV`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val v = View(context)
        FocusScaleHelper.attach(v)
        assertNull(v.onFocusChangeListener)
        assertFalse(v.isFocusable)
    }
}
