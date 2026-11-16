package com.zabtv.app.data

import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.data.net.AppProxySelector
import com.zabtv.app.data.net.CleartextGate
import com.zabtv.app.data.net.DohConfig
import com.zabtv.app.data.net.ProxyConfig
import com.zabtv.app.data.net.ProxyType
import okhttp3.Authenticator
import okhttp3.dnsoverhttps.DnsOverHttps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SECCION-11 (T-P2-2 · D10/A2-04): el bootstrap DoH HEREDA el
 * proxyAuthenticator. Con proxy que exige auth y sin authenticator, el 407
 * mata el resolver → outage silencioso de TODO el transporte (import/EPG/
 * logos/playback). Autenticando, el DoH sigue dentro del túnel Y funciona.
 * Degradar a DNS del sistema añadiría una fuga de DNS activa: NO se degrada.
 *
 * Nota OkHttp: "sin authenticator" es Authenticator.NONE (nunca null).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DohBootstrapAuthTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val container get() = AppContainer(context)

    private val proxyOn = AppContainer.NetPolicy(
        proxy = ProxyConfig(
            enabled = true,
            host = "proxy.example.com",
            port = 8080,
            type = ProxyType.HTTP,
        ),
    )
    private val auth = "u" to "p"

    @Test
    fun `1 bootstrap con auth hereda gate selector y authenticator`() {
        val bs = container.buildBootstrap(proxyOn, auth)
        assertTrue("el gate debe ser el interceptor[0]", bs.interceptors[0] is CleartextGate)
        assertTrue(
            "el selector debe ser el AppProxySelector de la política",
            bs.proxySelector is AppProxySelector,
        )
        assertTrue(
            "el bootstrap debe autenticar ante el proxy (407 mata el resolver)",
            bs.proxyAuthenticator != Authenticator.NONE,
        )
    }

    @Test
    fun `2 bootstrap sin auth no autentica`() {
        val bs = container.buildBootstrap(proxyOn, null)
        assertEquals(Authenticator.NONE, bs.proxyAuthenticator)
    }

    @Test
    fun `3 combo proxy auth y doh sin outage silencioso`() {
        val combo = proxyOn.copy(doh = DohConfig.Cloudflare)
        val client = container.buildOkHttp(combo, auth)
        assertTrue("el DoH debe seguir instalado", client.dns is DnsOverHttps)
        assertTrue(
            "el principal debe autenticar ante el proxy",
            client.proxyAuthenticator != Authenticator.NONE,
        )
        assertTrue("el gate sigue siendo el interceptor[0]", client.interceptors[0] is CleartextGate)
    }
}
