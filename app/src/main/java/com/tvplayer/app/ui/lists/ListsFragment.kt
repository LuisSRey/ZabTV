package com.tvplayer.app.ui.lists

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.TvNavigator
import com.tvplayer.app.data.db.ListEntity
import com.tvplayer.app.data.portal.SecureCredentials
import com.tvplayer.app.data.portal.XtreamRepository
import com.tvplayer.app.data.repo.ImportService
import com.tvplayer.app.data.repo.ImportSource
import com.tvplayer.app.ui.common.FocusScaleHelper
import kotlinx.coroutines.launch

/**
 * Gestión de listas (Task 11): alta por URL (+UA opcional) o archivo, borrado,
 * refresco manual (reimporta; el guard <10 % del import se muestra en el Toast)
 * y apertura del browser. Límite del spec: 10 listas.
 */
class ListsFragment : Fragment() {

    companion object {
        const val TAG = "lists"
        const val MAX_LISTS = 10
    }

    private lateinit var adapter: ListsAdapter
    private lateinit var emptyView: TextView

    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) addFromFile(uri)
    }

    private val pickDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) addFromFile(uri)
    }

    private val requestStorage = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showLocalBrowser() else fallbackPickers()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_lists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val addUrl = view.findViewById<Button>(R.id.lists_add_url)
        val addFile = view.findViewById<Button>(R.id.lists_add_file)
        emptyView = view.findViewById(R.id.lists_empty)
        val list = view.findViewById<RecyclerView>(R.id.lists_list)
        FocusScaleHelper.attach(addUrl)
        FocusScaleHelper.attach(addFile)

        adapter = ListsAdapter(
            onOpen = { row ->
                (activity as? TvNavigator.NavHost)?.openBrowser(row.id)
            },
            onRefresh = { row -> refresh(row.id) },
            onDelete = { row -> delete(row.id) },
            onEdit = { row -> edit(row.id) },
            onEpg = { row -> refreshEpgIfNeeded(row.id, announce = true) },
            onActivate = { row ->
                lifecycleScope.launch {
                    com.tvplayer.app.data.ActiveList.set(container().db, row.id)
                    Toast.makeText(requireContext(), R.string.lists_activated, Toast.LENGTH_SHORT).show()
                    reload()
                }
            },
        )
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        addUrl.setOnClickListener {
            AddListDialog { input -> onAddInput(input) }
                .show(parentFragmentManager, "add_list")
        }
        addFile.setOnClickListener {
            // Navegador propio en API ≤29 (permiso con sentido); en 30+ el
            // permiso no da acceso a .m3u ajenos: picker del sistema.
            if (android.os.Build.VERSION.SDK_INT <= 29) {
                if (hasStoragePermission()) {
                    showLocalBrowser()
                } else {
                    runCatching {
                        requestStorage.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }.onFailure { fallbackPickers() }
                }
            } else {
                fallbackPickers()
            }
        }

        reload()
    }

    private fun container() = (requireActivity().application as TpApp).container

    private fun reload() {
        lifecycleScope.launch {
            val db = container().db
            val lists = db.listDao().all()
            val activeId = com.tvplayer.app.data.ActiveList.resolve(db)
            emptyView.visibility = if (lists.isEmpty()) View.VISIBLE else View.GONE
            adapter.submit(
                lists.map { entity ->
                    val count = db.channelDao().countForList(entity.id)
                    val activeMark = if (entity.id == activeId) " • ${getString(R.string.lists_active)}" else ""
                    val sub = "${entity.status} • $count canales$activeMark"
                    ListsAdapter.Row(entity.id, entity.name, sub, entity.id == activeId)
                },
            )
        }
    }

    private fun onAddInput(input: AddListInput) {
        when (input) {
            is AddListInput.Url -> addFromUrl(input.name, input.url, input.userAgent, input.epgUrl)
            is AddListInput.Portal -> addFromPortal(input.name, input.host, input.username, input.password)
        }
    }

    private fun addFromUrl(name: String, url: String, userAgent: String?, epgUrl: String?) {
        lifecycleScope.launch {
            val db = container().db
            if (db.listDao().all().size >= MAX_LISTS) {
                Toast.makeText(requireContext(), R.string.lists_max, Toast.LENGTH_LONG).show()
                return@launch
            }
            val listId = db.listDao().upsert(
                ListEntity(
                    name = name.ifBlank { url },
                    sourceUrl = url,
                    userAgent = userAgent,
                    epgUrl = epgUrl,
                    status = "importing",
                ),
            )
            reload()
            val result = ImportService(
                requireContext().applicationContext, db, container().okHttp,
            ).import(listId, ImportSource.HttpSource(url, userAgent))
            Toast.makeText(requireContext(), safeMessage(result.message), Toast.LENGTH_LONG).show()
            if (!result.rejected) refreshEpgIfNeeded(listId)
            reload()
        }
    }

    private fun hasStoragePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED

    private fun showLocalBrowser() {
        LocalFileDialog { file ->
            addFromFile(android.net.Uri.fromFile(file))
        }.show(parentFragmentManager, "local_file")
    }

    private fun fallbackPickers() {
        // Picker del sistema (doble intento); si no hay ninguno, aviso.
        val opened = runCatching { pickFile.launch("*/*"); true }.getOrDefault(false) ||
            runCatching { pickDoc.launch(arrayOf("*/*")); true }.getOrDefault(false)
        if (!opened) {
            Toast.makeText(requireContext(), R.string.lists_no_picker, Toast.LENGTH_LONG).show()
        }
    }

    private fun addFromFile(uri: Uri) {
        // OPEN_DOCUMENT permite permiso persistente (reimporta sin pedir otra vez).
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        lifecycleScope.launch {
            val db = container().db
            if (db.listDao().all().size >= MAX_LISTS) {
                Toast.makeText(requireContext(), R.string.lists_max, Toast.LENGTH_LONG).show()
                return@launch
            }
            val name = uri.lastPathSegment ?: "Archivo"
            val listId = db.listDao().upsert(
                ListEntity(name = name, sourceUrl = "", status = "importing"),
            )
            reload()
            val result = ImportService(
                requireContext().applicationContext, db, container().okHttp,
            ).import(listId, ImportSource.FileSource(uri))
            Toast.makeText(requireContext(), safeMessage(result.message), Toast.LENGTH_LONG).show()
            if (!result.rejected) refreshEpgIfNeeded(listId)
            reload()
        }
    }

    /**
     * Portal Xtream: auth contra player_api + playlist get.php (m3u_plus/ts) vía
     * [XtreamRepository] sobre el OkHttp compartido (mismo CleartextGate);
     * 401/creds malas → mensaje específico sin tocar nada; Live solo (poda VOD).
     */
    private fun addFromPortal(name: String, host: String, username: String, password: String) {
        lifecycleScope.launch {
            val db = container().db
            if (db.listDao().all().size >= MAX_LISTS) {
                Toast.makeText(requireContext(), R.string.lists_max, Toast.LENGTH_LONG).show()
                return@launch
            }
            val appCtx = requireContext().applicationContext
            val repo = XtreamRepository(db, container().okHttp, appCtx, SecureCredentials.createEncrypted(appCtx))
            val result = repo.importPortal(null, name, host, username, password)
            Toast.makeText(requireContext(), safeMessage(result.message), Toast.LENGTH_LONG).show()
            if (!result.rejected) refreshEpgIfNeeded(result.listId)
            reload()
        }
    }

    /**
     * EPG automática tras importar: si la lista trae epgUrl (cabecera M3U o
     * portal), ingiere la guía para que "ahora/siguiente" funcione sin esperar
     * al worker de 6 h. Silenciosa salvo éxito con programas.
     */
    private fun refreshEpgIfNeeded(listId: Long, announce: Boolean = false) {
        lifecycleScope.launch {
            val db = container().db
            val epgUrl = db.listDao().get(listId)?.epgUrl
            if (epgUrl.isNullOrBlank()) {
                if (announce) toast(R.string.epg_no_source)
                return@launch
            }
            val res = runCatching {
                com.tvplayer.app.data.repo.EpgService(
                    requireContext().applicationContext, db, container().okHttp,
                ).refresh(epgUrl)
            }.getOrNull()
            if (res == null || res.rejected) {
                toast(safeEpgMessage(res?.message))
                return@launch
            }
            if (res.inserted > 0 || announce) {
                toast(getString(R.string.epg_refreshed, res.inserted))
            }
        }
    }

    private fun safeEpgMessage(message: String?): String =
        safeMessage(message).ifBlank { getString(R.string.portal_import_error) }

    private fun toast(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }

    private fun toast(resId: Int) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    }

    /**
     * Sanea mensajes que puedan llevar credenciales en la URL y mapea 401 a
     * mensaje específico. Nunca se muestra una credencial en pantalla.
     */
    private fun safeMessage(message: String?): String {
        if (message == null) return getString(R.string.portal_import_error)
        if (message.contains("username=") || message.contains("password=")) {
            return getString(R.string.portal_import_error)
        }
        if (message.contains("401")) return getString(R.string.portal_invalid)
        return message
    }

    /** Editar lista existente (nombre/URL/UA o datos del portal + reimport si cambió el origen). */
    private fun edit(listId: Long) {
        lifecycleScope.launch {
            val db = container().db
            val entity = db.listDao().get(listId) ?: return@launch
            val initial: AddListInput = if (entity.portalHost != null) {
                val creds = com.tvplayer.app.data.portal.SecureCredentials
                    .createEncrypted(requireContext().applicationContext).load(listId)
                AddListInput.Portal(
                    name = entity.name,
                    host = entity.portalHost!!,
                    username = creds?.first.orEmpty(),
                    password = creds?.second.orEmpty(),
                )
            } else {
                AddListInput.Url(
                    name = entity.name,
                    url = entity.sourceUrl,
                    userAgent = entity.userAgent,
                    epgUrl = entity.epgUrl,
                )
            }
            AddListDialog(initial) { input -> applyEdit(listId, entity, input) }
                .show(parentFragmentManager, "edit_list")
        }
    }

    private fun applyEdit(listId: Long, old: com.tvplayer.app.data.db.ListEntity, input: AddListInput) {
        when (input) {
            is AddListInput.Url -> {
                val changed = input.url != old.sourceUrl || input.userAgent != old.userAgent
                val epgChanged = (input.epgUrl.orEmpty()) != (old.epgUrl.orEmpty())
                lifecycleScope.launch {
                    val db = container().db
                    db.listDao().upsert(
                        old.copy(
                            name = input.name.ifBlank { old.name },
                            sourceUrl = input.url,
                            userAgent = input.userAgent,
                            epgUrl = input.epgUrl,
                            status = if (changed) "importing" else old.status,
                        ),
                    )
                    reload()
                    if (changed && input.url.isNotBlank()) {
                        val result = com.tvplayer.app.data.repo.ImportService(
                            requireContext().applicationContext, db, container().okHttp,
                        ).import(listId, com.tvplayer.app.data.repo.ImportSource.HttpSource(input.url, input.userAgent))
                        Toast.makeText(requireContext(), safeMessage(result.message), Toast.LENGTH_LONG).show()
                        if (!result.rejected) refreshEpgIfNeeded(listId)
                        reload()
                    } else if (epgChanged) {
                        // Lista de archivo o misma URL: solo cambió la guía → descargarla ya.
                        refreshEpgIfNeeded(listId, announce = true)
                        reload()
                    } else {
                        Toast.makeText(requireContext(), R.string.lists_updated, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            is AddListInput.Portal -> {
                lifecycleScope.launch {
                    val db = container().db
                    val appCtx = requireContext().applicationContext
                    val secure = SecureCredentials.createEncrypted(appCtx)
                    val creds = secure.load(listId)
                    val changed = input.host != old.portalHost ||
                        input.username != (creds?.first.orEmpty()) ||
                        input.password != (creds?.second.orEmpty())
                    if (!changed) {
                        db.listDao().upsert(old.copy(name = input.name.ifBlank { old.name }))
                        reload()
                        Toast.makeText(requireContext(), R.string.lists_updated, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    // Reimport del portal (auth + Live solo) con el OkHttp único.
                    val repo = XtreamRepository(db, container().okHttp, appCtx, secure)
                    val result = repo.importPortal(listId, input.name, input.host, input.username, input.password)
                    Toast.makeText(requireContext(), safeMessage(result.message), Toast.LENGTH_LONG).show()
                    if (!result.rejected) refreshEpgIfNeeded(listId)
                    reload()
                }
            }
        }
    }

    private fun refresh(listId: Long) {
        lifecycleScope.launch {
            val db = container().db
            val entity = db.listDao().get(listId) ?: return@launch
            // Portal Xtream: reimport con host + creds guardadas (auth player_api
            // + poda Live), mismo OkHttp compartido. Listas M3U: reimport HTTP.
            if (entity.portalHost != null) {
                Toast.makeText(requireContext(), R.string.lists_refreshing, Toast.LENGTH_SHORT).show()
                val appCtx = requireContext().applicationContext
                val repo = XtreamRepository(db, container().okHttp, appCtx, SecureCredentials.createEncrypted(appCtx))
                val result = repo.reimport(listId, entity.userAgent)
                Toast.makeText(requireContext(), safeMessage(result.message), Toast.LENGTH_LONG).show()
                if (!result.rejected) refreshEpgIfNeeded(listId)
                reload()
                return@launch
            }
            if (entity.sourceUrl.isBlank()) {
                Toast.makeText(requireContext(), R.string.lists_refresh_file, Toast.LENGTH_LONG).show()
                return@launch
            }
            Toast.makeText(requireContext(), R.string.lists_refreshing, Toast.LENGTH_SHORT).show()
            val result = ImportService(
                requireContext().applicationContext, db, container().okHttp,
            ).import(listId, ImportSource.HttpSource(entity.sourceUrl, entity.userAgent))
            Toast.makeText(requireContext(), safeMessage(result.message), Toast.LENGTH_LONG).show()
            if (!result.rejected) refreshEpgIfNeeded(listId)
            reload()
        }
    }

    private fun delete(listId: Long) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.lists_delete)
            .setMessage(R.string.lists_confirm_delete)
            .setPositiveButton(R.string.lists_delete) { _, _ ->
                lifecycleScope.launch {
                    val db = container().db
                    db.channelDao().deleteForList(listId)
                    db.favouriteDao().deleteForList(listId)
                    db.recentDao().clearForList(listId)
                    SecureCredentials.createEncrypted(requireContext().applicationContext).clear(listId)
                    db.listDao().delete(listId)
                    com.tvplayer.app.data.ActiveList.clearIf(db, listId)
                    reload()
                }
            }
            .setNegativeButton(R.string.lists_cancel, null)
            .show()
    }
}
