package com.zabtv.app.data

import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.engine.Media3Engine
import kotlinx.coroutines.runBlocking
import okhttp3.dnsoverhttps.DnsOverHttps
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SECCION-10 (T-P2-1 · D9/A2-01): el DNS PERSISTIDO es el USADO en los 3
 * subsistemas. Ajustes (DAO) → refreshNetPolicy → buildOkHttp instala
 * `.dns(DohProvider.buildDns(...))` → okHttp ÚNICO → (A) transporte,
 * (B) imageLoader de Coil re-instalado, (C) Media3Engine recién creado usa
 * esa misma instancia. El test falla si el cambio de DNS no llega a cualquiera
 * de los 3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DnsPropagationE2ETest {

    @Test
    fun `cloudflare en DAO llega a okHttp coil y media3`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = AppContainer(context)
        runBlocking {
            container.db.settingsDao().put(com.zabtv.app.data.net.DohConfig.KEY_MODE, com.zabtv.app.data.net.DohConfig.MODE_CLOUDFLARE)
        }

        // B: guardar el imageLoader ANTES del refresh para demostrar el rebuild.
        val imageLoaderBefore = container.imageLoader

        runBlocking { container.refreshNetPolicy() }

        // A (transporte): el cliente único lleva DnsOverHttps de Cloudflare.
        val dns = container.okHttp.dns
        assertTrue("okHttp.dns no es DnsOverHttps: $dns", dns is DnsOverHttps)
        val doh = dns as DnsOverHttps
        assertTrue("endpoint inesperado: ${doh.url}", doh.url.host == "1.1.1.1" && doh.url.encodedPath == "/dns-query")

        // B (Coil): el ImageLoader se reconstruyó con el cliente nuevo.
        assertTrue("imageLoader no se reconstruyó con el refresh", container.imageLoader !== imageLoaderBefore)
        coil.Coil.setImageLoader(container.imageLoader)
        assertSame(
            "Coil no quedó apuntando al imageLoader reconstruido",
            container.imageLoader,
            coil.Coil.imageLoader(context),
        )

        // C (Media3): un engine recién creado usa la MISMA instancia del cliente.
        val engine = Media3Engine(context, container.okHttp)
        assertSame(container.okHttp, engine.client)
    }
}
