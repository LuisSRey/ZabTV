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
        engine = Media3Engine(requireContext(), container.okHttp)

        surface = view.findViewById(R.id.player_surface)
        overlay = view.findViewById(R.id.player_overlay)
        logo = view.findViewById(R.id.player_logo)
        title = view.findViewById(R.id.player_title)
        nowText = view.findViewById(R.id.player_now)
        progress = view.findViewById(R.id.player_progress)

        engine.bind(surface)

        val listId = arguments?.getLong(ARG_LIST_ID, -1L) ?: -1L
        val position = arguments?.getInt(ARG_POSITION, 0) ?: 0
        lifecycleScope.launch {
            current = container.db.channelDao().channelAtPosition(listId, position)
            if (current == null) {
                close()
                return@launch
            }
            title.text = current!!.displayName
            current!!.logoUrl?.let { logo.load(it) }
            nowText.text = getString(R.string.player_now_placeholder)
            showOverlay()
            engine.prepare(PlaybackSource.Progressive(current!!.url))
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                engine.state.collect { state ->
                    when (state) {
                        is PlayerState.Error -> {
                            hideOverlay()
                            showError(state.messageRes)
                        }
                        PlayerState.Playing -> hideOverlayDelayed()
                        PlayerState.Loading, PlayerState.Buffering -> progress.visibility = View.VISIBLE
                        else -> progress.visibility = View.GONE
                    }
                }
            }
        }

        // Zapping D-pad: arriba/abajo cambia canal sobre el orden de la lista; BACK cierra
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> { lifecycleScope.launch { zap(-1) }; true }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { lifecycleScope.launch { zap(1) }; true }
                    android.view.KeyEvent.KEYCODE_BACK -> { close(); true }
                    else -> false
                }
            } else {
                false
            }
        }
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
        engine.release()
        super.onDestroyView()
    }

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

    private fun showError(resId: Int) {
        title.setText(resId)
        title.visibility = View.VISIBLE
    }

    /** Zapping: delta +1 = siguiente en el orden de la lista, -1 = anterior. */
    private suspend fun zap(delta: Int) {
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
        nowText.text = getString(R.string.player_now_placeholder)
        hideOverlay()
        showOverlay()
        engine.prepare(PlaybackSource.Progressive(channel.url))
    }

    private fun close() {
        (activity as? TvNavigator.NavHost)?.open(
            com.tvplayer.app.ui.home.HomeFragment(),
            com.tvplayer.app.ui.home.HomeFragment.TAG,
        )
    }
}
