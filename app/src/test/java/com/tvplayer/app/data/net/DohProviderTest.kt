package com.tvplayer.app.data.net

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** S2: DohProvider — System devuelve el DNS de plataforma; el resto, DoH real. */
class DohProviderTest {

    private val bootstrap = OkHttpClient()

    @Test
    fun `System devuelve Dns SYSTEM`() {
        assertSame(Dns.SYSTEM, DohProvider.buildDns(DohConfig.System, bootstrap))
    }

    @Test
    fun `Cloudflare y Google devuelven DnsOverHttps RFC8484`() {
        assertTrue(DohProvider.buildDns(DohConfig.Cloudflare, bootstrap) is DnsOverHttps)
        assertTrue(DohProvider.buildDns(DohConfig.Google, bootstrap) is DnsOverHttps)
    }

    @Test
    fun `Custom valida devuelve DnsOverHttps`() {
        val dns = DohProvider.buildDns(DohConfig.Custom("https://mi.dns/dns-query"), bootstrap)
        assertTrue(dns is DnsOverHttps)
    }

    @Test
    fun `Custom invalida cae a SYSTEM sin red`() {
        assertSame(Dns.SYSTEM, DohProvider.buildDns(DohConfig.Custom("http://inseguro/x"), bootstrap))
    }

    @Test
    fun `bootstrap sin dns propio no recursa`() {
        // El bootstrap que usa AppContainer es un cliente plano (Dns.SYSTEM):
        // verificar que el Dns construido no es el propio bootstrap disfrazado.
        val dns = DohProvider.buildDns(DohConfig.Cloudflare, bootstrap)
        assertTrue(dns !== Dns.SYSTEM)
    }
}
