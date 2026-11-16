package com.zabtv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SECCION-13 (T-P2-4 · D12/A1-10): paridad de árbol extendida —
 * (a) todo fragment_*.xml / activity_*.xml de res/layout/ tiene gemelo en
 * res/layout-television/ (los item_*.xml quedan EXCLUIDOS con rationale:
 * ítems compartidos adaptados por código; item_list/item_list_picker se usan
 * dentro de pantallas que ya tienen variante TV);
 * (b) para cada archivo presente en layout-television/ y layout-land-television/
 * el CONJUNTO de android:id debe ser idéntico (detector de drift estructural,
 * no de texto; land-television es copia manual — riesgo A1-10).
 * Patrón ExternalQueriesTest (lee XML como texto, JVM puro).
 */
class TvLayoutParityTest {

    private fun dir(name: String): File = File("src/main/res/$name")

    private fun ids(xml: String): Set<String> =
        Regex("android:id=\"@\\+id/([A-Za-z0-9_]+)\"").findAll(xml).map { it.groupValues[1] }.toSet()

    @Test
    fun `a todo fragment y activity tiene variante tv`() {
        val tvFiles = dir("layout-television").listFiles { f -> f.extension == "xml" }!!
            .map { it.name }.toSet()
        val missing = dir("layout").listFiles { f -> f.extension == "xml" }!!
            .map { it.name }
            .filter { it.startsWith("fragment_") || it.startsWith("activity_") }
            .filter { it !in tvFiles }
        assertTrue(
            "fragmentos sin variante TV: $missing (item_* excluidos por rationale)",
            missing.isEmpty(),
        )
    }

    @Test
    fun `b land-television es espejo por conjunto de ids`() {
        val tvNames = dir("layout-television").listFiles { f -> f.extension == "xml" }!!
            .map { it.name }.toSet()
        val landNames = dir("layout-land-television").listFiles { f -> f.extension == "xml" }!!
            .map { it.name }.toSet()
        val shared = tvNames intersect landNames
        assertTrue(
            "debe existir al menos el browser en ambos (escudo land-television)",
            shared.contains("fragment_channel_browser.xml"),
        )
        for (name in shared) {
            val tvIds = ids(dir("layout-television").resolve(name).readText())
            val landIds = ids(dir("layout-land-television").resolve(name).readText())
            assertEquals(
                "$name drift de ids entre layout-television y layout-land-television " +
                    "(falta en TV: ${(landIds - tvIds).joinToString()}; falta en land: ${(tvIds - landIds).joinToString()})",
                tvIds,
                landIds,
            )
        }
    }
}
