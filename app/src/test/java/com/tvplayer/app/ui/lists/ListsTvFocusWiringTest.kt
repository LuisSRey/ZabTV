package com.tvplayer.app.ui.lists

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * FRENTE L S5: cableado D-pad mínimo de "Mis listas" TV — add_url baja a la
 * fila y va a la derecha a add_file, add_file vuelve a add_url, la lista sube a
 * add_url y nav_lists entra a add_url (simetría con nav_browse). El foco dentro
 * de la fila recorre botones habilitados (activate disabled se salta). Móvil sin
 * nextFocus (sin cambio).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "television")
class ListsTvFocusWiringTest {

    private fun fragmentRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context).inflate(R.layout.fragment_lists, parent, false)
    }

    private fun shellRoot(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context).inflate(R.layout.activity_main, parent, false)
    }

    @Test
    fun `W2 tv nextFocus del fragment`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = fragmentRoot(context)
        val addUrl = root.findViewById<View>(R.id.lists_add_url)
        val addFile = root.findViewById<View>(R.id.lists_add_file)
        val list = root.findViewById<View>(R.id.lists_list)
        assertEquals(R.id.lists_list, addUrl.nextFocusDownId)
        assertEquals(R.id.lists_add_file, addUrl.nextFocusRightId)
        assertEquals(R.id.lists_add_url, addFile.nextFocusLeftId)
        assertEquals(R.id.lists_add_url, list.nextFocusUpId)
    }

    @Test
    fun `W2 tv nav_lists entra a add_url y nav_browse intacto`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = shellRoot(context)
        assertEquals(
            R.id.lists_add_url,
            root.findViewById<View>(R.id.nav_lists).nextFocusRightId,
        )
        // Simetría: nav_browse ya apuntaba al selector del browser.
        assertEquals(
            R.id.browser_list_selector,
            root.findViewById<View>(R.id.nav_browse).nextFocusRightId,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-port")
    fun `W2 movil sin nextFocus`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fragment = fragmentRoot(context)
        assertEquals(View.NO_ID, fragment.findViewById<View>(R.id.lists_add_url).nextFocusDownId)
        assertEquals(View.NO_ID, fragment.findViewById<View>(R.id.lists_add_url).nextFocusRightId)
        assertEquals(View.NO_ID, fragment.findViewById<View>(R.id.lists_add_file).nextFocusLeftId)
        assertEquals(View.NO_ID, fragment.findViewById<View>(R.id.lists_list).nextFocusUpId)
        val shell = shellRoot(context)
        assertEquals(View.NO_ID, shell.findViewById<View>(R.id.nav_lists).nextFocusRightId)
    }

    @Test
    fun `W3 DOWN desde add_url entra en fila 0`() {
        val (_, root, list) = listScreen(
            ListsAdapter.Row(1, "Uno", "ready", false),
            ListsAdapter.Row(2, "Dos", "ready", false),
        )
        assertTrue(list.childCount >= 1)
        val addUrl = root.findViewById<View>(R.id.lists_add_url)
        var target = addUrl.focusSearch(View.FOCUS_DOWN)
        // nextFocusDown apunta a la lista; el segundo salto cae en el primer
        // botón de la fila 0 (si el RecyclerView ya no es el candidato, el
        // primer salto lo resuelve).
        if (target?.id == R.id.lists_list) {
            target = target.focusSearch(View.FOCUS_DOWN)
        }
        assertEquals(R.id.list_open, target?.id)
        assertTrue(target!!.isFocusable)
        assertTrue(target.isEnabled)
    }

    @Test
    fun `W4 RIGHT recorre habilitados saltando activate disabled`() {
        val (_, _, list) = listScreen(ListsAdapter.Row(9, "Activa", "ready", true))
        val row = list.getChildAt(0)
        val open = row.findViewById<View>(R.id.list_open)
        val refresh = row.findViewById<View>(R.id.list_refresh)
        // open -> refresh -> delete -> (activate disabled) -> edit -> epg.
        val toRefresh = open.focusSearch(View.FOCUS_RIGHT)
        assertEquals(R.id.list_refresh, toRefresh?.id)
        val toDelete = toRefresh.focusSearch(View.FOCUS_RIGHT)
        assertEquals(R.id.list_delete, toDelete?.id)
        val toEdit = toDelete.focusSearch(View.FOCUS_RIGHT)
        assertEquals(R.id.list_edit, toEdit?.id)
        val toEpg = toEdit.focusSearch(View.FOCUS_RIGHT)
        assertEquals(R.id.list_epg, toEpg?.id)
        // Vuelta a la izquierda.
        assertEquals(R.id.list_open, refresh.focusSearch(View.FOCUS_LEFT)?.id)
    }

    private fun listScreen(vararg rows: ListsAdapter.Row): Triple<Activity, View, RecyclerView> {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = LayoutInflater.from(activity)
            .inflate(R.layout.fragment_lists, FrameLayout(activity), false)
        activity.setContentView(root)
        val list = root.findViewById<RecyclerView>(R.id.lists_list)
        list.layoutManager = LinearLayoutManager(activity)
        val adapter = ListsAdapter({}, {}, {}, {}, {}, {})
        list.adapter = adapter
        adapter.submit(rows.toList())
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1920, 1080)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return Triple(activity, root, list)
    }
}
