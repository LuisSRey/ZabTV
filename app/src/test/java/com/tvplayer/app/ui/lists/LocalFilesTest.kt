package com.tvplayer.app.ui.lists

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Navegador local (ruling usuario): solo carpetas + .m3u/.m3u8,
 * carpetas primero. Puro JVM, sin framework.
 */
class LocalFilesTest {

    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    @Test
    fun `dirs primero y solo m3u`() {
        val root = tmp.root
        File(root, "sub").mkdir()
        File(root, "b.M3U8").writeText("#EXTM3U\n")
        File(root, "a.m3u").writeText("#EXTM3U\n")
        File(root, "nota.txt").writeText("x")

        val entries = LocalFiles.listEntries(root, root)

        assertEquals(listOf("sub", "a.m3u", "b.M3U8"), entries.map { it.file.name })
        assertTrue(entries[0].isDir)
    }

    @Test
    fun `fuera de la raiz ofrece subir`() {
        val sub = File(tmp.root, "sub").apply { mkdir() }
        val entries = LocalFiles.listEntries(sub, tmp.root)
        assertTrue(entries.first().isUp)
    }

    @Test
    fun `en la raiz no hay subir`() {
        val entries = LocalFiles.listEntries(tmp.root, tmp.root)
        assertTrue(entries.none { it.isUp })
    }
}
