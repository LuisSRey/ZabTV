package com.tvplayer.app.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * SECCION-01 (T-P0-1 · paso 4): los 5 strings de veto existen en values-en
 * (texto exacto resuelto por el framework — Robolectric arranca en en) y en
 * values (paridad de locales, chequeo de presencia por lectura de archivo —
 * patrón ExternalQueriesTest). El texto de cada veto es el mensaje accionable
 * de la matriz 2.1.3 del DESIGN. El texto español exacto vive en
 * ExternalVetoStringsEsTest (qualifiers="es").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExternalVetoStringsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `los 5 strings de veto existen con texto exacto en`() {
        assertEquals(
            "Unencrypted http:// channel. Turn on \"Allow unencrypted HTTP\" in Settings → External player to open it outside the app.",
            context.getString(R.string.player_ext_veto_cleartext),
        )
        assertEquals(
            "This channel already goes through the local AceStream engine: the external player cannot reach that internal proxy.",
            context.getString(R.string.player_ext_veto_loopback),
        )
        assertEquals(
            "The channel URL is not http/https (or is invalid): TVPlayer only opens http(s) links outside the app.",
            context.getString(R.string.player_ext_veto_url),
        )
        assertEquals(
            "The channel URL embeds a user and password: TVPlayer does not open it outside the app for security.",
            context.getString(R.string.player_ext_veto_userinfo),
        )
        assertEquals(
            "Could not open AceStream: install it, keep it running and try again.",
            context.getString(R.string.player_external_ace_fail),
        )
    }

    @Test
    fun `values trae los 5 strings de veto`() {
        val f = File("src/main/res/values/strings.xml")
        assertTrue("falta values/strings.xml", f.exists())
        val xml = f.readText()
        for (name in listOf(
            "player_ext_veto_cleartext",
            "player_ext_veto_loopback",
            "player_ext_veto_url",
            "player_ext_veto_userinfo",
            "player_external_ace_fail",
        )) {
            assertTrue("falta $name en values", xml.contains("""name="$name"""))
        }
    }
}

/** Texto español exacto (qualifiers="es" para resolver values/). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es")
class ExternalVetoStringsEsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `los 5 strings de veto existen con texto exacto es`() {
        assertEquals(
            "Canal http:// sin cifrar. Activa «Permitir HTTP sin cifrar» en Ajustes → Reproductor externo para abrirlo fuera de la app.",
            context.getString(R.string.player_ext_veto_cleartext),
        )
        assertEquals(
            "Este canal ya pasa por el motor AceStream local: el reproductor externo no puede alcanzar ese proxy interno.",
            context.getString(R.string.player_ext_veto_loopback),
        )
        assertEquals(
            "La URL del canal no es http/https (o es inválida): TVPlayer solo abre enlaces http(s) fuera de la app.",
            context.getString(R.string.player_ext_veto_url),
        )
        assertEquals(
            "La URL del canal incluye usuario y contraseña: TVPlayer no la abre fuera de la app por seguridad.",
            context.getString(R.string.player_ext_veto_userinfo),
        )
        assertEquals(
            "No se pudo abrir AceStream: instálala, déjala en ejecución y vuelve a intentarlo.",
            context.getString(R.string.player_external_ace_fail),
        )
    }
}
