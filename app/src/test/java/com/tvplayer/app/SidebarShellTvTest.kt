package com.tvplayer.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1 shell TV: sidebar 200dp (antes 280dp: ~80dp más de contenido en 720p)
 * + título con @id/app_title para el colapso de la S2. Resto intacto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "television")
class SidebarShellTvTest {

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun shellRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context)
            .inflate(R.layout.activity_main, parent, false)
    }

    @Test
    fun `S1 tv sidebar 200dp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = shellRoot(context)
        val sidebar = root.findViewById<View>(R.id.sidebar)
        assertTrue(sidebar != null)
        assertEquals(dp(context, 200), sidebar.layoutParams.width)
    }

    @Test
    fun `S1 tv app_title existe con nombre de app`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = shellRoot(context)
        val title = root.findViewById<TextView>(R.id.app_title)
        assertTrue(title != null)
        assertEquals(context.getString(R.string.app_name), title.text.toString())
    }

    @Test
    fun `S3 tv nav_browse derecha al selector F2`() {
        // §F2 (lado shell): nav_browse → browser_list_selector; el regreso
        // selector → nav_browse vive en el layout del fragment.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = shellRoot(context)
        val navBrowse = root.findViewById<View>(R.id.nav_browse)
        assertEquals(R.id.browser_list_selector, navBrowse.nextFocusRightId)
    }

    @Test
    fun `S1 tv resto intacto 5 navs + container`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = shellRoot(context)
        for (id in listOf(R.id.nav_home, R.id.nav_browse, R.id.nav_search, R.id.nav_lists, R.id.nav_settings)) {
            val row = root.findViewById<View>(id)
            assertTrue(row != null)
            assertTrue(row.isFocusable)
        }
        assertTrue(root.findViewById<View>(R.id.container) != null)
    }
}
