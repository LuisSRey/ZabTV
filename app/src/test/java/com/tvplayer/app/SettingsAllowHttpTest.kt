package com.tvplayer.app

import android.os.Looper
import android.widget.Switch
import android.widget.TextView
import com.tvplayer.app.data.AppContainer
import com.tvplayer.app.data.net.CleartextGate
import com.tvplayer.app.ui.settings.SettingsFragment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * SECCION-02 (T-P0-2 · D1/A3-01): interruptor «Permitir HTTP sin cifrar» en
 * Ajustes. Un solo mecanismo: el switch escribe KEY_ALLOW_HTTP (DAO) y ese
 * dato alimenta a CleartextGate vía refreshNetPolicy (el ÚNICO switch de
 * http://) y a ExternalGate. El init del switch lee el DAO FRESCO (no
 * currentPolicy): una escritura directa al DAO sin refresh se refleja igual.
 *
 * Harness como MainActivityAutoRouteTest: main real (sin setMain Unconfined —
 * con Main de test las continuaciones de Room tocan vistas fuera del hilo
 * principal) y looper bombeado por tareas hasta observar el efecto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsAllowHttpTest {

    private lateinit var activity: MainActivity

    private fun pumpUntil(timeoutMs: Long = 15_000, cond: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            check(System.currentTimeMillis() <= end) { "efecto sin observar" }
            looper.runToNextTask()
            Thread.sleep(20)
        }
    }

    private fun container() = (activity.application as TpApp).container

    private fun daoValue(): String? = runBlocking {
        container().db.settingsDao().get(AppContainer.KEY_ALLOW_HTTP, "false")
    }

    private fun openSettings() {
        activity.pushFlow(SettingsFragment(), SettingsFragment.TAG)
        Robolectric.flushForegroundThreadScheduler()
    }

    @Test
    fun `switch inicializa desde DAO fresco sin refreshNetPolicy`() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        runBlocking { container().db.settingsDao().put(AppContainer.KEY_ALLOW_HTTP, "true") }
        openSettings()
        val sw = activity.findViewById<Switch>(R.id.settings_allow_http)
        pumpUntil { sw.isChecked }
        val state = activity.findViewById<TextView>(R.id.settings_http_state)
        assertEquals(activity.getString(R.string.settings_http_on), state.text.toString())
    }

    @Test
    fun `toggle a false escribe DAO y refresca el transporte`() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        runBlocking { container().db.settingsDao().put(AppContainer.KEY_ALLOW_HTTP, "true") }
        runBlocking { container().refreshNetPolicy() } // estado de arranque sincronizado
        openSettings()
        val sw = activity.findViewById<Switch>(R.id.settings_allow_http)
        val state = activity.findViewById<TextView>(R.id.settings_http_state)
        pumpUntil { sw.isChecked }
        sw.performClick() // false
        // el paint corre tras put+refreshNetPolicy: al verlo, el DAO y el transporte ya están
        pumpUntil { state.text.toString() == activity.getString(R.string.settings_http_off) }
        assertEquals("false", daoValue())
        val c = container()
        assertTrue(c.okHttp.interceptors[0] is CleartextGate)
        assertEquals(false, c.allowHttp) // estado refresh
    }

    @Test
    fun `toggle a true escribe DAO y refresca el transporte`() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        openSettings()
        val sw = activity.findViewById<Switch>(R.id.settings_allow_http)
        val state = activity.findViewById<TextView>(R.id.settings_http_state)
        pumpUntil { !sw.isChecked }
        sw.performClick() // true
        pumpUntil { state.text.toString() == activity.getString(R.string.settings_http_on) }
        assertEquals("true", daoValue())
        val c = container()
        assertTrue(c.okHttp.interceptors[0] is CleartextGate)
        assertEquals(true, c.allowHttp) // estado refresh
    }
}
