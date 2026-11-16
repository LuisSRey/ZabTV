package com.zabtv.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S4: PlaybackSource expone rawUrl + mimeType para el Intent externo. */
class PlaybackSourceMimeTest {

    @Test
    fun `Hls conserva url y declara mpegurl`() {
        val s = PlaybackSource.Hls("https://cdn.example.com/canal.m3u8")
        assertEquals("https://cdn.example.com/canal.m3u8", s.rawUrl())
        assertEquals("application/vnd.apple.mpegurl", s.mimeType())
    }

    @Test
    fun `Dash conserva url y declara dash+xml`() {
        val s = PlaybackSource.Dash("https://cdn.example.com/canal.mpd")
        assertEquals("https://cdn.example.com/canal.mpd", s.rawUrl())
        assertEquals("application/dash+xml", s.mimeType())
    }

    @Test
    fun `Progressive conserva url sin mime especifico`() {
        val s = PlaybackSource.Progressive("https://cdn.example.com/canal.ts")
        assertEquals("https://cdn.example.com/canal.ts", s.rawUrl())
        assertNull(s.mimeType())
    }
}
