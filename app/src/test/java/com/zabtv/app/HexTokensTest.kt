package com.zabtv.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SECCION-13 (T-P2-4 · D12/A1-07): cero hex literales fuera de tokens en la
 * LISTA CERRADA de archivos (no grep global — el resto de res/ está cubierto
 * por tokens o valores legítimos como ic_launcher). Cada archivo de la lista
 * debe usar @color/tp_*.
 */
class HexTokensTest {

    /** Lista cerrada del spec 2.4 (los únicos con hex fuera de tokens). */
    private val files = listOf(
        "src/main/res/drawable/tile_focus_bg.xml",
        "src/main/res/drawable/btn_primary.xml",
        "src/main/res/layout/fragment_home.xml",
        "src/main/res/layout-television/fragment_home.xml",
        "src/main/res/layout/activity_main.xml",
        "src/main/res/layout-television/activity_main.xml",
        "src/main/res/layout/fragment_player.xml",
        "src/main/res/layout-television/fragment_player.xml",
    )

    @Test
    fun `la lista cerrada existe`() {
        for (f in files) {
            assertTrue("falta $f", File(f).exists())
        }
    }

    @Test
    fun `sin hex literales en la lista cerrada`() {
        val hex = Regex("""#[0-9A-Fa-f]{6,8}\b""")
        for (f in files) {
            val xml = File(f).readText()
            val hits = hex.findAll(xml).map { it.value }.distinct().toList()
            assertFalse("$f contiene hex literales: $hits", hits.isNotEmpty())
        }
    }
}
