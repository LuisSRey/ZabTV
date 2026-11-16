package com.zabtv.app.data.portal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 12: lógica de claves de SecureCredentials sobre prefs real en disco.
 * Ruling (ledger): el Keystore real no existe en JVM — el blob cifrado de
 * producción (createEncrypted) se verifica en dispositivo (Task 16).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureCredentialsTest {

    private lateinit var context: Context
    private lateinit var creds: SecureCredentials

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("test_creds", Context.MODE_PRIVATE).edit().clear().commit()
        creds = SecureCredentials(
            context.getSharedPreferences("test_creds", Context.MODE_PRIVATE),
        )
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("test_creds", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `roundtrip guarda y lee user y pass`() {
        creds.save(42L, "miusuario", "s3cr3t-p4ss")
        val (user, pass) = creds.load(42L)!!
        assertEquals("miusuario", user)
        assertEquals("s3cr3t-p4ss", pass)
    }

    @Test
    fun `listas distintas no colisionan`() {
        creds.save(1L, "u1", "p1")
        creds.save(2L, "u2", "p2")
        assertEquals("p1", creds.load(1L)!!.second)
        assertEquals("p2", creds.load(2L)!!.second)
        creds.clear(1L)
        assertNull(creds.load(1L))
        assertEquals("u2", creds.load(2L)!!.first)
    }

    @Test
    fun `par incompleto devuelve null`() {
        creds.save(9L, "u", "p")
        context.getSharedPreferences("test_creds", Context.MODE_PRIVATE)
            .edit().remove("p_9").commit()
        assertNull(creds.load(9L))
    }
}
