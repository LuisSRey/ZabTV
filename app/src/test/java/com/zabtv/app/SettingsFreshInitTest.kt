package com.zabtv.app

import android.os.Looper
import android.widget.Switch
import com.zabtv.app.data.AppContainer
import com.zabtv.app.ui.settings.SettingsFragment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * SECCION-06 (T-P0-6 · D11/A3-06): abrir Ajustes muestra los switches con el
 * valor del DAO aunque currentPolicy esté rancio (escritura directa sin
 * refresh, p. ej. carrera con el arranque de la app).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsFreshInitTest {

    private fun pumpUntil(timeoutMs: Long = 15_000, cond: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            check(System.currentTimeMillis() <= end) { "efecto sin observar" }
            looper.runToNextTask()
            Thread.sleep(20)
        }
    }

    @Test
    fun `switches arrancan con el valor del DAO`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val container = (activity.application as TpApp).container
        runBlocking {
            container.db.settingsDao().put(AppContainer.KEY_EXTERNAL_GLOBAL, "true")
            container.db.settingsDao().put(AppContainer.KEY_ALLOW_HTTP, "true")
        }
        activity.pushFlow(SettingsFragment(), SettingsFragment.TAG)
        val global = activity.findViewById<Switch>(R.id.settings_external_global)
        val allowHttp = activity.findViewById<Switch>(R.id.settings_allow_http)
        pumpUntil { global.isChecked && allowHttp.isChecked }
        assertTrue(global.isChecked)
        assertTrue(allowHttp.isChecked)
    }
}
