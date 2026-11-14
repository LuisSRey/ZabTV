package com.tvplayer.app.ui.settings

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.Switch
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SECCION-02 (T-P0-2 · D1/A3-01, paso 2): el interruptor existe en AMBAS
 * variantes de fragment_settings (dual layout obligatorio) y en TV es
 * focable (D-pad). Patrón ListsTvFocusWiringTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsAllowHttpLayoutTest {

    private fun inflate(context: Context): View {
        val parent = FrameLayout(context)
        return LayoutInflater.from(context).inflate(R.layout.fragment_settings, parent, false)
    }

    @Test
    fun `movil trae switch y estado`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = inflate(context)
        val sw = root.findViewById<Switch>(R.id.settings_allow_http)
        val state = root.findViewById<TextView>(R.id.settings_http_state)
        assertTrue("falta settings_allow_http en móvil", sw is Switch)
        assertTrue("falta settings_http_state en móvil", state is TextView)
        assertEquals(
            "Allow unencrypted HTTP",
            context.getString(R.string.settings_http),
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "television")
    fun `tv trae switch focable y estado`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = inflate(context)
        val sw = root.findViewById<Switch>(R.id.settings_allow_http)
        val state = root.findViewById<TextView>(R.id.settings_http_state)
        assertTrue("falta settings_allow_http en TV", sw is Switch)
        assertTrue("falta settings_http_state en TV", state is TextView)
        assertTrue("el Switch TV debe ser focable", sw.isFocusable)
    }
}
