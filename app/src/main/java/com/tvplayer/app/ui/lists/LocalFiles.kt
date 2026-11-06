package com.tvplayer.app.ui.lists

import java.io.File

/**
 * Navegador local de listas (ruling usuario: permiso READ_EXTERNAL_STORAGE
 * solo hasta API 29 — en 30+ Android no deja leer .m3u ajenos sin permiso
 * total). Solo carpetas + .m3u/.m3u8, carpetas primero. Puro JVM.
 */
object LocalFiles {

    data class Entry(val file: File, val isDir: Boolean, val isUp: Boolean = false)

    fun listEntries(dir: File, root: File): List<Entry> {
        val out = ArrayList<Entry>()
        if (dir.absolutePath != root.absolutePath) {
            dir.parentFile?.let { out += Entry(it, isDir = true, isUp = true) }
        }
        val kids = dir.listFiles()?.toList().orEmpty()
        out += kids.filter { it.isDirectory }
            .sortedBy { it.name.lowercase() }
            .map { Entry(it, isDir = true) }
        out += kids.filter { it.isFile && (it.name.endsWith(".m3u", ignoreCase = true) || it.name.endsWith(".m3u8", ignoreCase = true)) }
            .sortedBy { it.name.lowercase() }
            .map { Entry(it, isDir = false) }
        return out
    }
}
