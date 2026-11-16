package com.zabtv.app.ui.common

import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.MainActivity
import com.zabtv.app.R
import com.zabtv.app.ui.channels.ChannelBrowserFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * S2 SidebarCollapseController: solo en tab browser el shell colapsa a
 * iconos (88dp, labels + app_title GONE); foco en nav_* expande (200dp
 * VISIBLE) y foco fuera colapsa; contentDescription = etiqueta (a11y sin
 * labels). syncShell/back stack/selección intactos (el controller nunca los
 * toca: ni isActivated ni colores ni visibilidad del chrome).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "television")
class SidebarCollapseControllerTest {

    private fun dp(value: Int): Int {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return (value * context.resources.displayMetrics.density).toInt()
    }

    @Test
    fun `shouldCollapse solo con tag browser`() {
        assertTrue(
            SidebarCollapseController.shouldCollapse(
                ChannelBrowserFragment.TAG, ChannelBrowserFragment.TAG,
            ),
        )
        assertFalse(SidebarCollapseController.shouldCollapse("home", ChannelBrowserFragment.TAG))
        assertFalse(SidebarCollapseController.shouldCollapse(null, ChannelBrowserFragment.TAG))
    }

    @Test
    fun `refresh en browser colapsa 88dp sin labels ni titulo`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        SidebarCollapseController.refresh(activity, ChannelBrowserFragment.TAG, ChannelBrowserFragment.TAG)
        val sidebar = activity.findViewById<View>(R.id.sidebar)
        assertEquals(dp(88), sidebar.layoutParams.width)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.nav_browse_label).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.app_title).visibility)
    }

    @Test
    fun `refresh fuera de browser expande 200dp con labels y titulo`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        SidebarCollapseController.refresh(activity, ChannelBrowserFragment.TAG, ChannelBrowserFragment.TAG)
        SidebarCollapseController.refresh(activity, "home", ChannelBrowserFragment.TAG)
        val sidebar = activity.findViewById<View>(R.id.sidebar)
        assertEquals(dp(200), sidebar.layoutParams.width)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.nav_browse_label).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.app_title).visibility)
    }

    @Test
    fun `attach pone contentDescription igual a la etiqueta`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        var tag = "home"
        SidebarCollapseController.attach(activity, { tag }, ChannelBrowserFragment.TAG)
        assertEquals(
            activity.getString(R.string.browser_title),
            activity.findViewById<View>(R.id.nav_browse).contentDescription,
        )
        assertEquals(
            activity.getString(R.string.nav_home),
            activity.findViewById<View>(R.id.nav_home).contentDescription,
        )
    }

    @Test
    fun `foco en nav expande y foco fuera colapsa solo en browser`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        var tag = ChannelBrowserFragment.TAG
        SidebarCollapseController.attach(activity, { tag }, ChannelBrowserFragment.TAG)
        SidebarCollapseController.refresh(activity, tag, ChannelBrowserFragment.TAG)
        val sidebar = activity.findViewById<View>(R.id.sidebar)
        val nav = activity.findViewById<View>(R.id.nav_browse)
        val container = activity.findViewById<View>(R.id.container)
        // Foco real (el listener a mano no mueve el foco y hasFocus mentiría).
        nav.isFocusableInTouchMode = true
        container.isFocusable = true
        container.isFocusableInTouchMode = true
        assertEquals(dp(88), sidebar.layoutParams.width)

        // Foco en nav_* → 200dp VISIBLE.
        assertTrue(nav.requestFocus())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(dp(200), sidebar.layoutParams.width)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.nav_browse_label).visibility)

        // Foco fuera del sidebar → colapsa (el check va posteado).
        assertTrue(container.requestFocus())
        repeat(3) { shadowOf(Looper.getMainLooper()).idle() }
        assertEquals(dp(88), sidebar.layoutParams.width)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.nav_browse_label).visibility)

        // Fuera de browser el foco no colapsa (el cambio de tab re-expande
        // vía refresh, como hace MainActivity tras syncShell).
        tag = "home"
        SidebarCollapseController.refresh(activity, tag, ChannelBrowserFragment.TAG)
        assertEquals(dp(200), sidebar.layoutParams.width)
        assertTrue(nav.requestFocus())
        assertTrue(container.requestFocus())
        repeat(3) { shadowOf(Looper.getMainLooper()).idle() }
        assertEquals(dp(200), sidebar.layoutParams.width)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.nav_browse_label).visibility)
    }

    @Test
    fun `refresh no toca seleccion ni chrome`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val nav = activity.findViewById<View>(R.id.nav_home)
        val label = activity.findViewById<TextView>(R.id.nav_home_label)
        val labelColor = label.currentTextColor
        SidebarCollapseController.refresh(activity, ChannelBrowserFragment.TAG, ChannelBrowserFragment.TAG)
        // Selección (activated + color) y chrome (sidebar VISIBLE) intactos.
        assertEquals(nav.isActivated, activity.findViewById<View>(R.id.nav_home).isActivated)
        assertEquals(labelColor, label.currentTextColor)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.sidebar).visibility)
        assertNull(activity.findViewById<View>(R.id.bottom_bar))
    }
}
