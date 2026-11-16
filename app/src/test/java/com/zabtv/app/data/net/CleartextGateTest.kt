package com.zabtv.app.data.net

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 11 (spec F8): CleartextGate es el ÚNICO punto que permite/bloquea http://.
 * OFF (default) → la llamada falla ANTES de conectar, con mensaje accionable.
 * ON → el 200 del servidor pasa.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CleartextGateTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(allowHttp: Boolean) = OkHttpClient.Builder()
        .addInterceptor(CleartextGate { allowHttp })
        .build()

    @Test
    fun `OFF bloquea http antes de conectar con mensaje accionable`() = runTest {
        server.enqueue(MockResponse().setBody("nunca se sirve"))
        val call = client(allowHttp = false).newCall(
            Request.Builder().url(server.url("/lista.m3u").toString()).build(),
        )
        try {
            call.execute().close()
            fail("debía bloquear el cleartext")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("https://"))
        }
        assertEquals(0, server.requestCount) // nada llegó al servidor
    }

    @Test
    fun `ON deja pasar http`() = runTest {
        server.enqueue(MockResponse().setBody("#EXTM3U\n"))
        client(allowHttp = true).newCall(
            Request.Builder().url(server.url("/lista.m3u").toString()).build(),
        ).execute().use { resp ->
            assertEquals(200, resp.code)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `loopback pasa aunque este OFF (proxy AceStream local)`() = runTest {
        // El gate no debe bloquear loopback por política: si la llamada falla,
        // que sea por red (puerto 9 cerrado), nunca con el mensaje de Ajustes.
        val gateClient = OkHttpClient.Builder().addInterceptor(CleartextGate { false }).build()
        try {
            gateClient.newCall(
                Request.Builder().url("http://127.0.0.1:9/lista.m3u").build(),
            ).execute().close()
            fail("debía fallar por red, no por política")
        } catch (e: IOException) {
            assertTrue(!e.message.orEmpty().contains("Ajustes"))
        }
    }

    @Test
    fun `https siempre pasa aunque esté OFF`() = runTest {
        // Sin red: lo que importa es que el gate NO la bloquee (falla por red, no por gate)
        val call = client(allowHttp = false).newCall(
            Request.Builder().url("https://127.0.0.1:9/lista.m3u").build(),
        )
        try {
            call.execute().close()
            fail("debía fallar por red")
        } catch (e: IOException) {
            assertTrue(!e.message.orEmpty().contains("Ajustes"))
        }
    }
}
