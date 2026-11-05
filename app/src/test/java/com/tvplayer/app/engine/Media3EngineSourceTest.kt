package com.tvplayer.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test de la capa de fuentes + mapeo de errores (Task 8).
 * `PlaybackSource.forUrl` y `Media3Engine.errorFor` son decidibles
 * sin player ni contexto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Media3EngineSourceTest {

    // ---- PlaybackSource.forUrl: tipo por extensión / Content-Type ----

    @Test
    fun `extensión m3u8 resuelve HLS`() {
        assertEquals(PlaybackSource.Hls("https://s/lista.m3u8"), PlaybackSource.forUrl("https://s/lista.m3u8"))
    }

    @Test
    fun `extensión mpd resuelve DASH`() {
        assertEquals(PlaybackSource.Dash("https://s/lista.mpd"), PlaybackSource.forUrl("https://s/lista.mpd"))
    }

    @Test
    fun `extensión ts mp4 mkv resuelve Progressive`() {
        assertEquals(PlaybackSource.Progressive("https://s/canal.ts"), PlaybackSource.forUrl("https://s/canal.ts"))
        assertEquals(PlaybackSource.Progressive("https://s/canal.mp4"), PlaybackSource.forUrl("https://s/canal.mp4"))
        assertEquals(PlaybackSource.Progressive("https://s/canal.mkv"), PlaybackSource.forUrl("https://s/canal.mkv"))
    }

    @Test
    fun `URL sin extensión usa Content-Type si se provee`() {
        assertEquals(
            PlaybackSource.Hls("https://s/stream"),
            PlaybackSource.forUrl("https://s/stream", contentType = "application/vnd.apple.mpegurl"),
        )
    }

    // ---- PlaybackSource.forUrl: rechazo antes de tocar red o player (spec F8) ----

    @Test(expected = IllegalArgumentException::class)
    fun `esquema no permitido falla antes de crear el player`() {
        PlaybackSource.forUrl("acestream://hash/stream")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `http bloqueado cuando cleartext está OFF falla antes de crear el player`() {
        PlaybackSource.forUrl("http://s/lista.m3u8", allowCleartext = false)
    }

    @Test
    fun `http permitido cuando cleartext está ON`() {
        assertEquals(
            PlaybackSource.Hls("http://s/lista.m3u8"),
            PlaybackSource.forUrl("http://s/lista.m3u8", allowCleartext = true),
        )
    }

    // ---- Media3Engine.errorFor: mapeo a PlayerError con mensaje accionable ----

    @Test
    fun `error de source mapea a NETWORK`() {
        val cause = androidx.media3.exoplayer.ExoPlaybackException.createForSource(
            java.io.IOException("sin red"),
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        )
        val err = Media3Engine.errorFor(cause)
        assertEquals(PlayerError.Code.NETWORK, err.code)
        assertTrue(err.userMessageRes != 0)
    }

    @Test
    fun `cualquier otro error es GENERIC`() {
        val err = Media3Engine.errorFor(IllegalStateException("inesperado"))
        assertEquals(PlayerError.Code.GENERIC, err.code)
    }
}
