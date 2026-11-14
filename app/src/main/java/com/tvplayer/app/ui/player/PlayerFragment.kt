package com.tvplayer.app.ui.player

import android.os.Bundle
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.tvplayer.app.engine.ExternalGate
import com.tvplayer.app.engine.ExternalOpener
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

        /**
         * S2: inmersivo solo en móvil apaisado. Pura (sin ventana) → test JVM.
         * En TV y en vertical el chrome del sistema se deja intacto.
         */
        fun shouldUseImmersive(isTv: Boolean, orientation: Int): Boolean =
            !isTv && orientation == Configuration.ORIENTATION_LANDSCAPE

        /**
         * S3: PiP opt-in solo en móvil con feature del sistema (SDK 26+).
         * Pura → test JVM. NUNCA auto-enter: la entrada es solo por tap
         * explícito en `player_pip` (botón que el layout TV ni siquiera trae).
         */
        fun pipAllowed(sdkInt: Int, hasFeature: Boolean, isTv: Boolean): Boolean =
            sdkInt >= android.os.Build.VERSION_CODES.O && hasFeature && !isTv

        /** Visibilidad del botón PiP según la política (TV: el botón no existe, null-seguro). */
        fun pipButtonVisibility(allowed: Boolean): Int =
            if (allowed) View.VISIBLE else View.GONE

        /** En PiP el overlay estorba a la ventana flotante: se oculta (el engine sigue). Pura. */
        fun shouldHideOverlayInPip(inPip: Boolean): Boolean = inPip
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
    /** True en ventana flotante (PiP): overlay oculto, engine en marcha. */
    private var inPip = false
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
        applyImmersiveIfNeeded()

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
        // S4: reproductor externo (opt-in Global/Siempre/Nunca; función separada de PiP).
        view.findViewById<ImageView>(R.id.player_external)?.let { ext ->
            ext.setOnClickListener { openExternal() }
            if (isTv) FocusScaleHelper.attach(ext)
        }
        // S3: PiP opt-in SOLO móvil (el layout TV no trae player_pip → null, no-op).
        // NUNCA auto-enter: solo este tap explícito llama a enterPip().
        view.findViewById<ImageView>(R.id.player_pip)?.let { pip ->
            pip.visibility = pipButtonVisibility(isPipSupported())
            pip.setOnClickListener { enterPip() }
        }
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
            container.db.recentDao().recordWatch(featured.listId, featured.stableKey)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // El manifest evita la recreación al rotar: reevaluar aquí.
        applyImmersiveIfNeeded()
    }

    /**
     * S3: PiP. Al entrar: overlay GONE + tap-catcher deshabilitado (el engine
     * sigue sonando). Al salir: se restaura el tap-catcher y el overlay.
     * NUNCA se entra automáticamente (sin auto-enter en onPause ni en zapping).
     */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        applyPipMode(isInPictureInPictureMode)
    }

    private fun applyPipMode(inPipMode: Boolean) {
        inPip = inPipMode
        val root = view ?: return
        val tap = root.findViewById<View>(R.id.player_tap) ?: return
        if (shouldHideOverlayInPip(inPipMode)) {
            overlayJob?.cancel()
            overlayJob = null
            if (::overlay.isInitialized) overlay.visibility = View.GONE
            tap.isEnabled = false
        } else {
            tap.isEnabled = true
            if (::overlay.isInitialized) showOverlay()
        }
    }

    /** ¿Soporta PiP este dispositivo ahora? TV → false siempre. */
    private fun isPipSupported(): Boolean {
        val tv = view?.let { FocusScaleHelper.isTv(it) } ?: false
        val hasFeature = try {
            requireContext().packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE,
            )
        } catch (_: Exception) {
            false
        }
        return pipAllowed(android.os.Build.VERSION.SDK_INT, hasFeature, tv)
    }

    /** Entrada manual a PiP 16:9. Solo por botón; nunca automática. */
    private fun enterPip() {
        if (!isPipSupported() || inPip) return
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val params = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
            .build()
        runCatching { requireActivity().enterPictureInPictureMode(params) }
    }

    override fun onPause() {
        super.onPause()
        // S2: al pausar (PiP, diálogo del motor, Home) devolver el chrome.
        restoreChrome()
    }

    override fun onDestroyView() {
        overlayJob?.cancel()
        dismissEngineDialog()
        restoreChrome()
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
        // Re-afirmar inmersivo al volver (diálogo del motor, PiP, etc.).
        applyImmersiveIfNeeded()
        if (engineDialog?.isShowing == true) {
            val channel = current
            if (channel != null && ready()) lifecycleScope.launch { prepareCurrent(channel) }
        }
    }

    /** El engine se crea en corrutina (lee el buffer de DAO); teclas tempranas se ignoran. */
    private fun ready(): Boolean = ::engine.isInitialized

    /**
     * S2: oculta las barras del sistema solo en móvil apaisado; en cualquier
     * otro caso restaura el chrome (rotar a vertical saliendo del inmersivo).
     */
    private fun applyImmersiveIfNeeded() {
        val activity = activity ?: return
        val view = view ?: return
        val controller = WindowInsetsControllerCompat(activity.window, view)
        if (shouldUseImmersive(FocusScaleHelper.isTv(view), resources.configuration.orientation)) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** S2: al pausar/salir, devolver siempre el chrome del sistema. */
    private fun restoreChrome() {
        val activity = activity ?: return
        val view = view ?: return
        WindowInsetsControllerCompat(activity.window, view)
            .show(WindowInsetsCompat.Type.systemBars())
    }

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

    /**
     * S4: abre el canal actual en reproductor externo (opt-in Global/Siempre/
     * Nunca). Nunca lanza: vetado o sin receptor → toast ESPECÍFICO del veto
     * (SECCION-04 · D3, matriz 2.1.3). El externo NO hereda proxy/DNS
     * (advertido en Settings y en la ficha del canal). Vía acestream del
     * botón = lanzar el intent de contenido del motor (lo que openChannel ya
     * hace); si falla → player_external_ace_fail. POLICY_OFF no toast (el
     * botón no debería estar visible: SECCION-05). El genérico
     * player_external_blocked queda eliminado de esta vía.
     */
    private fun openExternal() {
        val channel = current ?: return
        lifecycleScope.launch {
            val fresh = container.db.channelDao().channelByKey(channel.listId, channel.stableKey) ?: channel
            val proxy = com.tvplayer.app.data.AppContainer.DEFAULT_ACE_PROXY
            val engineTarget = AceStreamEngine.engineProxyFor(fresh.url, proxy)
            val source = try {
                PlaybackSource.forUrl(fresh.url, allowCleartext = container.allowHttp, aceStreamProxy = proxy)
            } catch (_: IllegalArgumentException) {
                null
            }
            val policy = container.currentPolicy
            val preferred = com.tvplayer.app.engine.ExternalPlayers.getPreferred(
                container.db.settingsDao(), com.tvplayer.app.data.AppContainer.KEY_EXTERNAL_PLAYER_PACKAGE,
            )
            val veto = ExternalGate.vetoOf(
                fresh.url, container.allowHttp, engineTarget, fresh.allowExternal, policy.externalGlobal,
            )
            val opened = when (veto) {
                // Sin veto → export con CANDIDATOS (directo o chooser).
                null -> {
                    val candidates = ExternalOpener.exportCandidates(fresh.url, source?.mimeType())
                    if (!preferred.isNullOrBlank() &&
                        !ExternalOpener.isPreferredUsable(
                            requireContext().packageManager, preferred, candidates,
                        )
                    ) {
                        Toast.makeText(
                            requireContext(), R.string.settings_external_gone, Toast.LENGTH_LONG,
                        ).show()
                    }
                    ExternalOpener.openChannel(
                        requireContext(), fresh.url, source, container.allowHttp,
                        engineTarget, fresh.allowExternal, policy.externalGlobal,
                        preferredPackage = preferred,
                    )
                }
                // ACESTREAM: vía oficial del motor (startContentIntent, como
                // openChannel); si el startActivity falla → aviso largo.
                ExternalGate.Veto.ACESTREAM -> {
                    val ok = ExternalOpener.openChannel(
                        requireContext(), fresh.url, source, container.allowHttp,
                        engineTarget, fresh.allowExternal, policy.externalGlobal,
                        preferredPackage = preferred,
                    )
                    if (!ok) {
                        Toast.makeText(
                            requireContext(), R.string.player_external_ace_fail, Toast.LENGTH_LONG,
                        ).show()
                    }
                    ok
                }
                // POLICY_OFF: el botón no debería estar visible (SECCION-05);
                // silencioso, coherente con el INTERNAL silencioso del auto-route.
                ExternalGate.Veto.POLICY_OFF -> false
                // Resto de vetos: SU mensaje (sin abrir nada: ya está el interno).
                else -> {
                    Toast.makeText(
                        requireContext(),
                        com.tvplayer.app.engine.ExternalVetoUi.messageRes(veto),
                        if (com.tvplayer.app.engine.ExternalVetoUi.longToast(veto)) {
                            Toast.LENGTH_LONG
                        } else {
                            Toast.LENGTH_SHORT
                        },
                    ).show()
                    false
                }
            }
            if (!opened && veto == null) {
                Toast.makeText(
                    requireContext(), R.string.player_no_external, Toast.LENGTH_SHORT,
                ).show()
            }
        }
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
        container.db.recentDao().recordWatch(channel.listId, channel.stableKey)
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
     * Vale para `acestream://` y para URLs ya proxificadas en loopback
     * (algunas listas las traen así); el proxy a chequear se pasa explícito.
     */
    private suspend fun ensureEngineRunning(aceUrl: String, proxy: String = com.tvplayer.app.data.AppContainer.DEFAULT_ACE_PROXY): Int {
        val (host, defaultPort) = AceStreamEngine.splitHostPort(proxy)
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
        val viewUrl = AceStreamEngine.aceStreamUrlForView(aceUrl)
        showEngineDialog(R.string.engine_no_service, offerOpen = true, aceUrl = viewUrl)
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
        // Necesita motor local tanto con `acestream://` como con URL ya
        // proxificada en loopback (algunas listas la traen así): sin este
        // chequeo el motor caído acababa en error de red en la guía.
        val engineTarget = AceStreamEngine.engineProxyFor(channel.url, proxy)
        android.util.Log.d("TvPlayer", "prepare ace=" + (engineTarget != null) + " url=" + channel.url.take(40))
        if (engineTarget != null) {
            val port = ensureEngineRunning(channel.url, engineTarget)
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
            (activity as? TvNavigator.NavHost)?.openTab(
                com.tvplayer.app.ui.home.HomeFragment(),
                com.tvplayer.app.ui.home.HomeFragment.TAG,
            )
        }
    }
}
