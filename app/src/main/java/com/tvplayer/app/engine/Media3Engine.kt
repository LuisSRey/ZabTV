package com.tvplayer.app.engine

import android.content.Context
import android.view.SurfaceView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.tvplayer.app.R
import com.tvplayer.app.data.BufferConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Motor de reproducción sobre Media3 1.11.1 (Task 8). Todo el transporte de red pasa
 * por el OkHttp compartido (AppContainer) — el único punto donde vive CleartextGate (Task 11).
 *
 * Buffer del spec (§6): back-buffer 8 s / min 15 s / max 30 s.
 *
 * CONTRATO DE SESIÓN EN CURSO (SECCION-06 · A2-02, comportamiento aceptado):
 * el engine conserva el cliente con el que se creó; los cambios de
 * DNS/proxy/cleartext aplican a la SIGUIENTE preparación (nuevo engine al
 * reabrir el player), no a la sesión en marcha (ver AppContainer.rebuildOkHttp).
 */
class Media3Engine(
    private val context: Context,
    private val client: okhttp3.OkHttpClient,
    private val buffer: BufferConfig = BufferConfig(),
) : PlaybackEngine {

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    buffer.minMs,
                    buffer.maxMs,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                )
                .setBackBuffer(buffer.backMs, false)
                .build(),
        )
        .build()

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    override val state: StateFlow<PlayerState> = _state

    private val dataSourceFactory: DataSource.Factory =
        OkHttpDataSource.Factory(client).setUserAgent("TVPlayer/0.1 (Linux; AndroidTV)")

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = when {
                    player.playbackState == Player.STATE_IDLE -> PlayerState.Idle
                    player.playbackState == Player.STATE_BUFFERING -> PlayerState.Buffering
                    player.playbackState == Player.STATE_READY -> if (isPlaying) PlayerState.Playing else PlayerState.Paused
                    else -> PlayerState.Loading
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                onIsPlayingChanged(player.isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.value = PlayerState.Error(errorFor(error).userMessageRes)
            }
        })
    }

    override fun bind(surface: SurfaceView) {
        player.setVideoSurfaceView(surface)
    }

    override suspend fun prepare(source: PlaybackSource) {
        _state.value = PlayerState.Loading
        val mediaItem = MediaItem.fromUri(urlOf(source))
        val mediaSource = when (source) {
            is PlaybackSource.Hls -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            is PlaybackSource.Dash -> DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            is PlaybackSource.Progressive -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }
        player.setMediaSource(mediaSource)
        player.prepare()
        player.setPlayWhenReady(true)
    }

    override fun play() = player.play()
    override fun pause() = player.pause()

    /** ExoPlayer subyacente: PlayerFragment lo asigna al PlayerView para aspecto correcto. */
    val exoPlayer: ExoPlayer get() = player

    /** Salto relativo (mando Fire TV: FF/REW ±30 s). Clamp a [0, duración]. */
    override fun seekBy(millis: Long) {
        val duration = player.duration.takeIf { it > 0 } ?: return
        player.seekTo((player.currentPosition + millis).coerceIn(0, duration))
    }

    override fun release() = player.release()

    private fun urlOf(source: PlaybackSource): String = when (source) {
        is PlaybackSource.Hls -> source.url
        is PlaybackSource.Dash -> source.url
        is PlaybackSource.Progressive -> source.url
    }

    override fun errorFor(cause: Throwable): PlayerError = Companion.errorFor(cause)

    /**
     * Mapeo de errores de Media3 a PlayerError con mensaje accionable (spec §6.8):
     * clasifica por tipo de ExoPlaybackException (source = red, renderer = formato)
     * y por errorCode para el caso específico de cleartext bloqueado.
     */
    companion object {
        fun errorFor(cause: Throwable): PlayerError = when {
            cause is ExoPlaybackException && cause.type == ExoPlaybackException.TYPE_SOURCE ->
                PlayerError(PlayerError.Code.NETWORK, R.string.error_network)
            cause is ExoPlaybackException && cause.type == ExoPlaybackException.TYPE_RENDERER ->
                PlayerError(PlayerError.Code.UNSUPPORTED_FORMAT, R.string.error_unsupported_format)
            cause is PlaybackException && cause.errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
                PlayerError(PlayerError.Code.CLEARTEXT_BLOCKED, R.string.error_cleartext)
            cause is PlaybackException && cause.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                PlayerError(PlayerError.Code.SERVER, R.string.error_server)
            else -> PlayerError(PlayerError.Code.GENERIC, R.string.error_generic)
        }
    }
}

/** Estados expuestos al PlayerViewModel. */
sealed class PlayerState {
    object Idle : PlayerState()
    object Loading : PlayerState()
    object Playing : PlayerState()
    object Paused : PlayerState()
    object Buffering : PlayerState()
    data class Error(val messageRes: Int) : PlayerState()
}

/** Contrato del motor (Task 8). AceStream queda fuera por ToS — la abstracción lo permite en futuro. */
interface PlaybackEngine {
    fun bind(surface: SurfaceView)
    suspend fun prepare(source: PlaybackSource)
    fun play()
    fun pause()
    /** Salto relativo en ms (positivo = adelante). */
    fun seekBy(millis: Long)
    fun release()
    val state: StateFlow<PlayerState>
    fun errorFor(cause: Throwable): PlayerError
}
