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
import com.tvplayer.app.engine.AceStreamEngine
import com.tvplayer.app.engine.Media3Engine
import com.tvplayer.app.engine.PlaybackSource
import com.tvplayer.app.engine.PlayerState
import com.tvplayer.app.ui.common.FocusScaleHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /** True si el canal aún no llegó a engine.prepare (motor sin arrancar o error). */
    private var needsPrepare = true
    private var overlayJob: kotlinx.coroutines.Job? = null
    private var engineDialog: android.app.AlertDialog? = null

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
            // DIAG: hasta dónde llega el arranque (el tap no pisa prepareCurrent).
            android.util.Log.d("TvPlayer", "player launch listId=" + listId + " pos=" + position)
            // Engine con el buffer configurado (DAO) — se crea aquí porque load es suspend.
            engine = Media3Engine(requireContext(), container.okHttp, com.tvplayer.app.data.BufferConfig.load(container.db))
            engine.bind(surface)
            playerView.player = engine.exoPlayer
            current = container.db.channelDao().channelAtPosition(listId, position)
            android.util.Log.d("TvPlayer", "player channel=" + (current?.displayName ?: "NULL"))
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
            val featured = current!!
            com.tvplayer.app.data.LastPlayed.save(container.db, featured.listId, featured.stableKey)
            prepareCurrent(featured)

            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                engine.state.collect { state ->
                    view.findViewById<ImageView>(R.id.player_playpause)?.setImageResource(
                        if (state == PlayerState.Playing) R.drawable.ic_pause else R.drawable.ic_play,
                    )
                    when (state) {
                        is PlayerState.Error -> {
                            progress.visibility = View.GONE
                            android.util.Log.d("TvPlayer", "player error res=" + state.messageRes)
                            showError(state.messageRes)
                        }
                        PlayerState.Playing -> {
                            progress.visibility = View.GONE
                            hideOverlayDelayed()
                        }
                        PlayerState.Loading, PlayerState.Buffering -> {
                            overlay.visibility = View.VISIBLE
                            progress.visibility = View.VISIBLE
                        }
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
        dismissEngineDialog()
        if (ready()) engine.release()
        super.onDestroyView()
    }

    /**
     * Al volver de la UI del motor (flujo manual AceServe): si el diálogo
     * seguía abierto, reintenta solo — sin tap extra — cuando el proxy ya
     * responde reproduce y el diálogo se cierra; si no, se re-muestra.
     */
    override fun onResume() {
        super.onResume()
        if (engineDialog?.isShowing == true) {
            val channel = current
            if (channel != null && ready()) lifecycleScope.launch { prepareCurrent(channel) }
        }
    }

    /** El engine se crea en corrutina (lee el buffer de DAO); teclas tempranas se ignoran. */
    private fun ready(): Boolean = ::engine.isInitialized

    private fun showOverlay() {
        overlay.visibility = View.VISIBLE
        overlayJob?.cancel()
        overlayJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(5_000)
            // Sin esto, un arranque P2P lento (>5 s) deja pantalla negra sin info.
            if (ready() && engine.state.value == PlayerState.Playing) hideOverlay()
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
        // Tras un error (o con el motor aún sin arrancar), play reintenta la
        // preparación en vez de hacer no-op sobre un engine vacío.
        val channel = current
        if ((engine.state.value is PlayerState.Error ||
                (needsPrepare && engine.state.value != PlayerState.Loading)) && channel != null) {
            lifecycleScope.launch { prepareCurrent(channel) }
            return
        }
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
        // El overlay DEBE verse (antes se ocultaba → negro total) pero el título
        // conserva el canal: el error va en la línea de "ahora".
        overlay.visibility = View.VISIBLE
        overlayJob?.cancel()
        overlayJob = null
        nowText.setText(resId)
        nowText.visibility = View.VISIBLE
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
        com.tvplayer.app.data.LastPlayed.save(container.db, channel.listId, channel.stableKey)
        hideOverlay()
        showOverlay()
        dismissEngineDialog()
        prepareCurrent(channel)
    }

    /**
     * Motor AceStream: fast-path si el proxy ya escucha (vale cualquier
     * motor: AceServe o AceStream). Con el motor caído NO se arranca nada
     * solo: se muestra el diálogo (Reintentar / Abrir motor / Cerrar) y la
     * línea de guía queda intacta. "Abrir motor" prioriza AceServe.
     */
    private suspend fun ensureEngineRunning(aceUrl: String): Int {
        val (host, defaultPort) = AceStreamEngine.splitHostPort(com.tvplayer.app.data.AppContainer.DEFAULT_ACE_PROXY)
        val up = withContext(kotlinx.coroutines.Dispatchers.IO) {
            AceStreamEngine.isReachable(host, defaultPort)
        }
        android.util.Log.d("TvPlayer", "ace reachable=$up host=$host port=$defaultPort")
        if (up) {
            dismissEngineDialog()
            return defaultPort
        }
        progress.visibility = View.GONE
        needsPrepare = true
        showEngineDialog(R.string.engine_no_service, offerOpen = true, aceUrl = aceUrl)
        return 0
    }

    /** Abre la UI del motor para arranque manual (AceServe no expone servicio). */
    private fun openEngineUi(aceUrl: String): Boolean = runCatching {
        val app = requireContext().applicationContext
        AceStreamEngine.launchIntent(app)?.let {
            startActivity(it)
            true
        } ?: run {
            startActivity(AceStreamEngine.viewIntent(aceUrl))
            true
        }
    }.getOrDefault(false)

    /**
     * Avisos del motor en diálogo (no en la línea de guía): Reintentar lanza
     * la preparación de nuevo, Abrir motor abre AceServe/AceStream para
     * arranque manual, Cerrar lo deja con la guía visible.
     */
    private fun showEngineDialog(msgRes: Int, offerOpen: Boolean, aceUrl: String? = null) {
        if (!isAdded) return
        val activity = activity
        if (activity == null || activity.isFinishing) return
        dismissEngineDialog()
        val builder = android.app.AlertDialog.Builder(activity)
            .setTitle(R.string.engine_dialog_title)
            .setMessage(msgRes)
            .setPositiveButton(R.string.engine_retry) { _, _ ->
                val channel = current
                if (channel != null) lifecycleScope.launch { prepareCurrent(channel) }
            }
            .setNegativeButton(R.string.engine_close, null)
        if (offerOpen && aceUrl != null) {
            builder.setNeutralButton(R.string.engine_open) { _, _ ->
                openEngineUi(aceUrl)
            }
        }
        engineDialog = runCatching { builder.show() }.getOrNull()
        android.util.Log.d("TvPlayer", "engine dialog shown=" + (engineDialog != null))
    }

    private fun dismissEngineDialog() {
        runCatching { engineDialog?.dismiss() }
        engineDialog = null
    }

    /**
     * Resuelve la fuente ANTES de tocar el player (spec F8): http con cleartext
     * OFF o esquema no permitido → error visible inmediato, sin pantalla negra.
     */
    private suspend fun prepareCurrent(channel: ChannelEntity) {
        var proxy = com.tvplayer.app.data.AppContainer.DEFAULT_ACE_PROXY
        val isAce = channel.url.startsWith("acestream://", ignoreCase = true)
        android.util.Log.d("TvPlayer", "prepare ace=$isAce url=" + channel.url.take(40))
        if (isAce) {
            val port = ensureEngineRunning(channel.url)
            if (port == 0) return
            proxy = "127.0.0.1:$port"
        }
        val source = try {
            PlaybackSource.forUrl(channel.url, allowCleartext = container.allowHttp, aceStreamProxy = proxy)
        } catch (e: IllegalArgumentException) {
            progress.visibility = View.GONE
            val msg = e.message.orEmpty()
            showError(
                when {
                    msg.contains("Texto plano") -> R.string.error_cleartext
                    msg.contains("AceStream") -> R.string.error_acestream
                    else -> R.string.error_unsupported_format
                },
            )
            return
        }
        engine.prepare(source)
        needsPrepare = false
        dismissEngineDialog()
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
        dismissEngineDialog()
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
