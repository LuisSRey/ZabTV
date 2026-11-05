package com.tvplayer.app.ui.lists

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.TvNavigator
import com.tvplayer.app.data.db.ListEntity
import com.tvplayer.app.data.portal.SecureCredentials
import com.tvplayer.app.data.portal.XtreamLogin
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
        addFile.setOnClickListener { pickFile.launch("*/*") }

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
            is AddListInput.Url -> addFromUrl(input.name, input.url, input.userAgent)
            is AddListInput.Portal -> addFromPortal(input.name, input.host, input.username, input.password)
        }
    }

    private fun addFromUrl(name: String, url: String, userAgent: String?) {
        lifecycleScope.launch {
            val db = container().db
            if (db.listDao().all().size >= MAX_LISTS) {
                Toast.makeText(requireContext(), R.string.lists_max, Toast.LENGTH_LONG).show()
                return@launch
            }
            val listId = db.listDao().upsert(
                ListEntity(name = name.ifBlank { url }, sourceUrl = url, userAgent = userAgent, status = "importing"),
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

    private fun addFromFile(uri: Uri) {
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

    /** Portal Xtream (Task 12): single request; 401 → mensaje específico; creds a Keystore. */
    private fun addFromPortal(name: String, host: String, username: String, password: String) {
        lifecycleScope.launch {
            val db = container().db
            if (db.listDao().all().size >= MAX_LISTS) {
                Toast.makeText(requireContext(), R.string.lists_max, Toast.LENGTH_LONG).show()
                return@launch
            }
            val playlistUrl: String
            val epgUrl: String?
            try {
                playlistUrl = XtreamLogin.playlistUrl(host, username, password)
                epgUrl = XtreamLogin.epgUrl(host, username, password)
            } catch (_: IllegalArgumentException) {
                Toast.makeText(requireContext(), R.string.portal_invalid_host, Toast.LENGTH_LONG).show()
                return@launch
            }
            val listId = db.listDao().upsert(
                ListEntity(
                    name = name.ifBlank { host },
                    sourceUrl = "",
                    epgUrl = epgUrl,
                    portalHost = host,
                    status = "importing",
                ),
            )
            // Credenciales cifradas ANTES de cualquier red (nunca a logs ni Toasts)
            SecureCredentials.createEncrypted(requireContext().applicationContext).save(listId, username, password)
            reload()
            val result = ImportService(
                requireContext().applicationContext, db, container().okHttp,
            ).import(listId, ImportSource.HttpSource(playlistUrl, null))
            val shown = portalMessage(result.message)
            Toast.makeText(requireContext(), shown, Toast.LENGTH_LONG).show()
            if (!result.rejected) refreshEpgIfNeeded(listId)
            reload()
        }
    }

    /**
     * EPG automática tras importar: si la lista trae epgUrl (cabecera M3U o
     * portal), ingiere la guía para que "ahora/siguiente" funcione sin esperar
     * al worker de 6 h. Silenciosa salvo éxito con programas.
     */
    private fun refreshEpgIfNeeded(listId: Long) {
        lifecycleScope.launch {
            val db = container().db
            val epgUrl = db.listDao().get(listId)?.epgUrl
            if (epgUrl.isNullOrBlank()) return@launch
            val res = runCatching {
                com.tvplayer.app.data.repo.EpgService(
                    requireContext().applicationContext, db, container().okHttp,
                ).refresh(epgUrl)
            }.getOrNull() ?: return@launch
            if (!res.rejected && res.inserted > 0) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.epg_refreshed, res.inserted),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
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

    private fun portalMessage(message: String?): String? = safeMessage(message)

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
                lifecycleScope.launch {
                    val db = container().db
                    db.listDao().upsert(
                        old.copy(
                            name = input.name.ifBlank { old.name },
                            sourceUrl = input.url,
                            userAgent = input.userAgent,
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
                    } else {
                        Toast.makeText(requireContext(), R.string.lists_updated, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            is AddListInput.Portal -> {
                lifecycleScope.launch {
                    val playlistUrl: String
                    val epgUrl: String?
                    try {
                        playlistUrl = com.tvplayer.app.data.portal.XtreamLogin.playlistUrl(input.host, input.username, input.password)
                        epgUrl = com.tvplayer.app.data.portal.XtreamLogin.epgUrl(input.host, input.username, input.password)
                    } catch (_: IllegalArgumentException) {
                        Toast.makeText(requireContext(), R.string.portal_invalid_host, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val db = container().db
                    val creds = com.tvplayer.app.data.portal.SecureCredentials
                        .createEncrypted(requireContext().applicationContext).load(listId)
                    val changed = input.host != old.portalHost ||
                        input.username != (creds?.first.orEmpty()) ||
                        input.password != (creds?.second.orEmpty())
                    db.listDao().upsert(
                        old.copy(
                            name = input.name.ifBlank { old.name },
                            epgUrl = epgUrl,
                            portalHost = input.host,
                            status = if (changed) "importing" else old.status,
                        ),
                    )
                    if (changed) {
                        com.tvplayer.app.data.portal.SecureCredentials
                            .createEncrypted(requireContext().applicationContext)
                            .save(listId, input.username, input.password)
                    }
                    reload()
                    if (changed) {
                        val result = com.tvplayer.app.data.repo.ImportService(
                            requireContext().applicationContext, db, container().okHttp,
                        ).import(listId, com.tvplayer.app.data.repo.ImportSource.HttpSource(playlistUrl, null))
                        Toast.makeText(requireContext(), safeMessage(result.message), Toast.LENGTH_LONG).show()
                        if (!result.rejected) refreshEpgIfNeeded(listId)
                        reload()
                    } else {
                        Toast.makeText(requireContext(), R.string.lists_updated, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun refresh(listId: Long) {
        lifecycleScope.launch {
            val db = container().db
            val entity = db.listDao().get(listId) ?: return@launch
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
                    db.listDao().delete(listId)
                    com.tvplayer.app.data.ActiveList.clearIf(db, listId)
                    reload()
                }
            }
            .setNegativeButton(R.string.lists_cancel, null)
            .show()
    }
}
