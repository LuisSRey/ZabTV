package com.zabtv.app.data.net

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.data.AppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1: CleartextGate SIGUE siendo el interceptor[0] tras introducir el proxy —
 * la política de texto-plano se evalúa antes que cualquier reenvío.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CleartextGateOrderTest {

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = AppContainer(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun `buildOkHttp instala CleartextGate como interceptor 0`() {
        val client = container.buildOkHttp(AppContainer.NetPolicy(), auth = null)
        assertTrue(client.interceptors.isNotEmpty())
        assertTrue(client.interceptors[0] is CleartextGate)
    }

    @Test
    fun `buildOkHttp con proxy instala selector y sin proxy usa default`() {
        val withProxy = container.buildOkHttp(
            AppContainer.NetPolicy(proxy = ProxyConfig(enabled = true, host = "p.local", port = 3128)),
            auth = null,
        )
        assertTrue(withProxy.proxySelector is AppProxySelector)
        val without = container.buildOkHttp(AppContainer.NetPolicy(), auth = null)
        assertTrue(without.proxySelector !is AppProxySelector)
    }
}

/**
 * S1: ProxyCreds — mismo patrón que SecureCredentials (prefs inyectado,
 * cifrado real solo en producción). Nunca loguea valores.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProxyCredsTest {

    private lateinit var creds: ProxyCreds

    private fun prefs() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("test_proxy_creds", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs().edit().clear().commit()
        creds = ProxyCreds(prefs())
    }

    @Test
    fun `roundtrip guarda y lee usuario y pass`() {
        creds.save("proxyuser", "s3cr3t")
        assertEquals("proxyuser" to "s3cr3t", creds.load())
    }

    @Test
    fun `sin guardar devuelve null`() {
        assertNull(creds.load())
    }

    @Test
    fun `clear borra el par completo`() {
        creds.save("u", "p")
        creds.clear()
        assertNull(creds.load())
    }

    @Test
    fun `par incompleto devuelve null`() {
        creds.save("u", "p")
        prefs().edit().remove("proxy_pass").commit()
        assertNull(creds.load())
    }
}
