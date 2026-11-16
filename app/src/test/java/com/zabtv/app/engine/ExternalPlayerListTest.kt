package com.zabtv.app.engine

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Selector de reproductor externo preferido: la lista de apps detectadas
 * sale sin duplicados, sin la propia app y ordenada por nombre.
 * La parte pura vive en [ExternalPlayers.sortedUnique]; el listado real
 * ([ExternalPlayers.listVideoPlayers]) solo añade PackageManager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExternalPlayerListTest {

    private fun info(pkg: String, label: String) = ExternalPlayerInfo(pkg, label)

    @Test
    fun `ordena por etiqueta insensible a mayusculas`() {
        val out = ExternalPlayers.sortedUnique(
            listOf(info("c.z", "Zeta"), info("a.m", "MX Player"), info("b.v", "VLC")),
            ownPackage = "com.zabtv.app",
        )
        assertEquals(listOf("MX Player", "VLC", "Zeta"), out.map { it.label })
    }

    @Test
    fun `sin duplicados por paquete`() {
        val out = ExternalPlayers.sortedUnique(
            listOf(info("c.dup", "Dup"), info("c.dup", "Dup"), info("c.otro", "Otro")),
            ownPackage = "com.zabtv.app",
        )
        assertEquals(listOf("c.dup", "c.otro"), out.map { it.packageName })
    }

    @Test
    fun `excluye a la propia app`() {
        val out = ExternalPlayers.sortedUnique(
            listOf(info("com.zabtv.app", "ZabTV"), info("c.vlc", "VLC")),
            ownPackage = "com.zabtv.app",
        )
        assertEquals(listOf("c.vlc"), out.map { it.packageName })
    }

    @Test
    fun `ignora paquetes en blanco`() {
        val out = ExternalPlayers.sortedUnique(
            listOf(info("", "Sin paquete"), info("c.vlc", "VLC")),
            ownPackage = "com.zabtv.app",
        )
        assertEquals(listOf("c.vlc"), out.map { it.packageName })
    }

    @Test
    fun `listado real sin duplicados sin propia app y ordenado`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val out = ExternalPlayers.listVideoPlayers(ctx)
        assertFalse(out.any { it.packageName == ctx.packageName })
        assertEquals(out.map { it.packageName }.toSet().size, out.size)
        assertEquals(out.map { it.label }, out.map { it.label }.sortedWith(String.CASE_INSENSITIVE_ORDER))
        assertTrue(out.none { it.packageName.isBlank() })
    }
}
