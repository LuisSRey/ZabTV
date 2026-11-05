package com.tvplayer.app.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.TvNavigator
import com.tvplayer.app.data.AppContainer
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.repo.FavouritesRepo
import com.tvplayer.app.engine.Media3Engine
import com.tvplayer.app.engine.PlaybackSource
import com.tvplayer.app.engine.PlayerState
import com.tvplayer.app.ui.common.FocusScaleHelper
import kotlinx.coroutines.launch

/**
 * Player de pantalla completa (Task 8): SurfaceView + overlay de 5 s con logo/nombre
 * del canal y "ahora" (placeholder hasta Task 14). Zapping arriba/abajo (D-pad TV,
 * flechas móvil) sobre el orden de la lista; long-press OK → favorito (Task 9);
 * BACK → cierra y libera el motor.
 *
 * v0.1: el click en canal del browser navega aquí vía TvNavigator.openPlayer.
 */
class PlayerFragment : Fragment() {

    companion object {
        const val TAG = "player"
        const val ARG_LIST_ID = "listId"
        const val ARG_POSITION = "position"

        fun newInstance(listId: Long, position: Int) = PlayerFragment().apply {
            arguments = bundleOf(ARG_LIST_ID to listId, ARG_POSITION to position)
        }
    }

    private lateinit var engine: Media3Engine
    private lateinit var container: AppContainer
    private lateinit var surface: SurfaceView
    private lateinit var overlay: View
    private lateinit var logo: ImageView
    private lateinit var title: TextView
    private lateinit var nowText: TextView
    private lateinit var progress: ProgressBar

    private var current: ChannelEntity? = null
    private var overlayJob: kotlinx.coroutines.Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val isTv = FocusScaleHelper.isTv(view)
        container = (requireActivity().application as TpApp).container

        val playerView = view.findViewById<androidx.media3.ui.PlayerView>(R.id.player_view)
        // PlayerView 1.11 expone la surface como View (soporta surface/texture).
        surface = requireNotNull(playerView.videoSurfaceView as? SurfaceView) { "PlayerView sin surface" }
        overlay = view.findViewById(R.id.player_overlay)
        // Tap en el vídeo ↔ overlay (imprescindible en táctil: el overlay se auto-oculta).
        // El long-press sigue en la raíz y burbujea desde el tap-catcher (no es long-clickable).
        view.findViewById<View>(R.id.player_tap).setOnClickListener { toggleOverlay() }
        // Flechas táctiles (móvil; en TV no existen → null seguro)
        view.findViewById<View>(R.id.player_zap_up)?.setOnClickListener {
            lifecycleScope.launch { zap(1) }
        }
        view.findViewById<View>(R.id.player_zap_down)?.setOnClickListener {
            lifecycleScope.launch { zap(-1) }
        }
        // Transporte ±10 s y play/pausa (móvil táctil + TV con D-pad).
        val rew = view.findViewById<ImageView>(R.id.player_rew)
        val playPause = view.findViewById<ImageView>(R.id.player_playpause)
        val ff = view.findViewById<ImageView>(R.id.player_ff)
        rew?.setOnClickListener { if (ready()) engine.seekBy(-10_000) }
        ff?.setOnClickListener { if (ready()) engine.seekBy(10_000) }
        playPause?.setOnClickListener { togglePlay() }
        if (isTv) {
            listOfNotNull(rew, playPause, ff).forEach { FocusScaleHelper.attach(it) }
        }
        logo = view.findViewById(R.id.player_logo)
        title = view.findViewById(R.id.player_title)
        nowText = view.findViewById(R.id.player_now)
        progress = view.findViewById(R.id.player_progress)

        val listId = arguments?.getLong(ARG_LIST_ID, -1L) ?: -1L
        val position = arguments?.getInt(ARG_POSITION, 0) ?: 0
        // UN SOLO launch secuencial ligado a la VISTA (fix crash + leak al volver atrás):
        // engine → canal → prepare → y SOLO DESPUÉS el collect del estado.
        viewLifecycleOwner.lifecycleScope.launch {
            // Engine con el buffer configurado (DAO) — se crea aquí porque load es suspend.
            engine = Media3Engine(requireContext(), container.okHttp, com.tvplayer.app.data.BufferConfig.load(container.db))
            engine.bind(surface)
            playerView.player = engine.exoPlayer
            current = container.db.channelDao().channelAtPosition(listId, position)
            if (current == null) {
                close()
                return@launch
            }
            title.text = current!!.displayName
            val lr = 8f * view.resources.displayMetrics.density
            current!!.logoUrl?.let { logo.load(it) {
                transformations(coil.transform.RoundedCornersTransformation(lr))
            } }
            updateNowLine(current!!)
            showOverlay()
            prepareCurrent(current!!)

            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                engine.state.collect { state ->
                    view.findViewById<ImageView>(R.id.player_playpause)?.setImageResource(
                        if (state == PlayerState.Playing) R.drawable.ic_pause else R.drawable.ic_play,
                    )
                    when (state) {
                        is PlayerState.Error -> {
                            progress.visibility = View.GONE
                            showError(state.messageRes)
                        }
                        PlayerState.Playing -> {
                            progress.visibility = View.GONE
                            hideOverlayDelayed()
                        }
                        PlayerState.Loading, PlayerState.Buffering -> progress.visibility = View.VISIBLE
                        else -> progress.visibility = View.GONE
                    }
                }
            }
        }

        // Zapping D-pad: arriba/abajo cambia canal; BACK cierra. Mando Fire TV:
        // teclas multimedia (play/pausa/stop/FF/REW) y OK muestra/oculta el overlay.
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> { lifecycleScope.launch { zap(-1) }; true }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { lifecycleScope.launch { zap(1) }; true }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER -> { toggleOverlay(); true }
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlay(); true }
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> { if (ready()) engine.play(); true }
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
                    android.view.KeyEvent.KEYCODE_MEDIA_STOP -> { if (ready()) engine.pause(); true }
                    android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (ready()) engine.seekBy(30_000); true }
                    android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> { if (ready()) engine.seekBy(-30_000); true }
                    else -> false
                }
            } else {
                false
            }
        }
        // BACK vía dispatcher (no depende del foco de la vista — mandos Fire TV).
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = close()
            },
        )
        // long-press OK → favorito (Task 9)
        view.setOnLongClickListener {
            val channel = current
            if (channel != null) {
                lifecycleScope.launch {
                    val listId = arguments?.getLong(ARG_LIST_ID, -1L) ?: -1L
                    val added = FavouritesRepo(container.db).toggle(listId, channel)
                    Toast.makeText(
                        requireContext(),
                        if (added) R.string.fav_added else R.string.fav_removed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            true
        }
    }

    override fun onDestroyView() {
        overlayJob?.cancel()
        if (ready()) engine.release()
        super.onDestroyView()
    }

    /** El engine se crea en corrutina (lee el buffer de DAO); teclas tempranas se ignoran. */
    private fun ready(): Boolean = ::engine.isInitialized

    private fun showOverlay() {
        overlay.visibility = View.VISIBLE
        overlayJob?.cancel()
        overlayJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(5_000)
            hideOverlay()
        }
    }

    private fun hideOverlayDelayed() {
        overlayJob?.cancel()
        overlayJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(3_000)
            hideOverlay()
        }
    }

    private fun hideOverlay() {
        overlay.visibility = View.GONE
        overlayJob = null
    }

    private fun togglePlay() {
        if (!ready()) return
        if (engine.state.value == PlayerState.Playing) engine.pause() else engine.play()
    }

    private fun toggleOverlay() {
        overlayJob?.cancel()
        overlayJob = null
        if (overlay.visibility == View.VISIBLE) {
            hideOverlay()
        } else {
            showOverlay()
        }
    }

    private fun showError(resId: Int) {
        // El overlay DEBE verse: es donde vive el mensaje (antes se ocultaba → negro total).
        overlay.visibility = View.VISIBLE
        overlayJob?.cancel()
        overlayJob = null
        title.setText(resId)
        title.visibility = View.VISIBLE
    }

    /** Zapping: delta +1 = siguiente en el orden de la lista, -1 = anterior. */
    private suspend fun zap(delta: Int) {
        if (!ready()) return
        val listId = arguments?.getLong(ARG_LIST_ID, -1L) ?: -1L
        val pos = current?.position ?: return
        val target: ChannelEntity? = if (delta > 0) {
            container.db.channelDao().nextInList(listId, pos)
        } else {
            container.db.channelDao().prevInList(listId, pos)
        }
        val channel: ChannelEntity = target ?: return
        current = channel
        title.text = channel.displayName
        channel.logoUrl?.let { logo.load(it) }
        updateNowLine(channel)
        hideOverlay()
        showOverlay()
        prepareCurrent(channel)
    }

    /**
     * Resuelve la fuente ANTES de tocar el player (spec F8): http con cleartext
     * OFF o esquema no permitido → error visible inmediato, sin pantalla negra.
     */
    private suspend fun prepareCurrent(channel: ChannelEntity) {
        val source = try {
            PlaybackSource.forUrl(channel.url, allowCleartext = container.allowHttp)
        } catch (e: IllegalArgumentException) {
            progress.visibility = View.GONE
            val res = if ((e.message ?: "").contains("Texto plano")) {
                R.string.error_cleartext
            } else {
                R.string.error_unsupported_format
            }
            showError(res)
            return
        }
        engine.prepare(source)
    }

    /** Línea "ahora" del overlay (Task 14): exacta o aproximada, o placeholder sin guía. */
    private suspend fun updateNowLine(channel: ChannelEntity) {
        val db = container.db
        val match = com.tvplayer.app.data.repo.EpgMatcher(db.epgDao()).match(channel)
        if (match == null) {
            nowText.text = getString(R.string.player_now_placeholder)
            return
        }
        val nn = db.epgDao().nowNext(listOf(match.tvgId), System.currentTimeMillis())
        val nowTitle = nn[match.tvgId]?.first?.title
        nowText.text = when {
            nowTitle != null && match.approximate -> getString(R.string.player_now_approx, nowTitle)
            nowTitle != null -> getString(R.string.player_now, nowTitle)
            else -> getString(R.string.player_now_placeholder)
        }
    }

    private fun close() {
        // Vuelve atrás en la pila (browser/lista anterior) con syncShell automático;
        // si no hay pila, cae a Home.
        val fm = activity?.supportFragmentManager
        if (fm != null && fm.backStackEntryCount > 0) {
            fm.popBackStack()
        } else {
            (activity as? TvNavigator.NavHost)?.open(
                com.tvplayer.app.ui.home.HomeFragment(),
                com.tvplayer.app.ui.home.HomeFragment.TAG,
            )
        }
    }
}
