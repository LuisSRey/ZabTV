package com.tvplayer.app.data.net

import java.net.InetAddress
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps

/**
 * Construye el [Dns] efectivo (S2). Los presets y el Custom válido devuelven
 * un [DnsOverHttps] RFC 8484; System y Custom inválido devuelven [Dns.SYSTEM].
 *
 * `bootstrap` es un cliente SIN dns propio (Dns.SYSTEM): las consultas DoH
 * salen por él y así no hay recursión (el DoH nunca se resuelve a sí mismo).
 * Hereda el proxy del cliente principal para no fugar DNS fuera del túnel.
 */
object DohProvider {

    fun buildDns(config: DohConfig, bootstrap: OkHttpClient): Dns = when (config) {
        is DohConfig.System -> Dns.SYSTEM
        is DohConfig.Cloudflare -> doh(
            bootstrap,
            "https://1.1.1.1/dns-query",
            "1.1.1.1", "1.0.0.1",
        )
        // Ver DohConfig: endpoint RFC 8484 oficial (8.8.8.8/resolve es JSON, incompatible).
        is DohConfig.Google -> doh(
            bootstrap,
            "https://dns.google/dns-query",
            "8.8.8.8", "8.8.4.4",
        )
        is DohConfig.Custom ->
            if (DohConfig.isValidHttpsUrl(config.url)) doh(bootstrap, config.url.trim()) else Dns.SYSTEM
    }

    private fun doh(bootstrap: OkHttpClient, url: String, vararg bootstrapIps: String): Dns =
        DnsOverHttps.Builder()
            .client(bootstrap)
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(bootstrapIps.map { InetAddress.getByName(it) })
            .build()
}
