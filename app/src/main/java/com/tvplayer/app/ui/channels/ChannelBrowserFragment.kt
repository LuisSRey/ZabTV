package com.tvplayer.app.ui.channels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.TvNavigator
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.engine.PlaybackSource
import com.tvplayer.app.ui.channel_dialog.ChannelSheetFragment
import com.tvplayer.app.ui.common.FocusScaleHelper
import com.tvplayer.app.ui.home.HomeFragment
import kotlinx.coroutines.launch

/**
 * Browser de canales dual (Task 7): layout-television = rail de grupos a la
 * izquierda + grilla 4 cols con foco D-pad; móvil = chips horizontales + grilla
 * 3 cols. Datos por PagingData — nunca la lista entera en RAM.
 *
 * v0.1 Task 7: el click en canal es un placeholder; Task 8 (Media3Engine)
 * reemplaza el Toast por navegación al reproductor.
 */
class ChannelBrowserFragment : Fragment() {

    companion object {
        const val TAG = "browser"
        const val ARG_LIST_ID = "listId"

        fun newInstance(listId: Long) = ChannelBrowserFragment().apply {
            arguments = bundleOf(ARG_LIST_ID to listId)
        }
    }

    private lateinit var viewModel: ChannelBrowserViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_channel_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val listId = arguments?.getLong(ARG_LIST_ID, -1L) ?: -1L
        if (listId < 0) {
            (activity as? TvNavigator.NavHost)?.open(HomeFragment(), HomeFragment.TAG)
            return
        }

        val isTv = FocusScaleHelper.isTv(view)
        val container = (requireActivity().application as TpApp).container
        viewModel = ChannelBrowserViewModel(container.db.channelDao(), listId)

        val grid = view.findViewById<RecyclerView>(R.id.channel_grid)
        val gridAdapter = ChannelGridAdapter(
            onClick = ::onChannelClick,
            onLongClick = { channel ->
                ChannelSheetFragment.newInstance(channel.listId, channel.stableKey)
                    .show(parentFragmentManager, ChannelSheetFragment.TAG)
            },
        )
        grid.layoutManager = GridLayoutManager(requireContext(), if (isTv) 4 else 3)
        grid.adapter = gridAdapter

        // Tira Ahora/Siguiente (Task 14): un batch nowNext por pausa de scroll.
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                val layout = recyclerView.layoutManager as? GridLayoutManager ?: return
                val first = layout.findFirstVisibleItemPosition()
                val last = layout.findLastVisibleItemPosition()
                if (first < 0 || last < first) return
                val ids = (first..last).mapNotNull { gridAdapter.peek(it)?.tvgId?.takeIf { t -> t.isNotBlank() } }
                    .distinct()
                lifecycleScope.launch {
                    val nn = container.db.epgDao().nowNext(ids, System.currentTimeMillis())
                    val lines = mutableMapOf<String, String>()
                    for (pos in first..last) {
                        val ch = gridAdapter.peek(pos) ?: continue
                        val tvgId = ch.tvgId?.takeIf { it.isNotBlank() } ?: continue
                        val (nowP, nextP) = nn[tvgId] ?: continue
                        val line = listOfNotNull(
                            nowP?.title?.let { "▶ $it" },
                            nextP?.title?.let { "· $it" },
                        ).joinToString(" ").takeIf { it.isNotBlank() } ?: continue
                        lines[ch.stableKey] = line
                    }
                    if (lines.isNotEmpty()) gridAdapter.submitEpg(lines)
                }
            }
        })

        val rail = view.findViewById<RecyclerView>(R.id.group_rail)
        lateinit var railAdapter: ChannelRailAdapter
        railAdapter = ChannelRailAdapter { row ->
            railAdapter.selected = row.group
            viewModel.select(row.group)
            grid.scrollToPosition(0)
        }
        rail.layoutManager = LinearLayoutManager(
            requireContext(),
            if (isTv) LinearLayoutManager.VERTICAL else LinearLayoutManager.HORIZONTAL,
            false,
        )
        rail.adapter = railAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // collectLatest: cada grupo nuevo cancela el submit anterior (si no,
                // el primer submitData se queda suspendido y la parrilla no cambia).
                viewModel.channels.collectLatest { pagingData -> gridAdapter.submitData(pagingData) }
            }
        }

        // Rail: consulta puntual por ciclo de vida (vuelve a leerse al re-entrar)
        lifecycleScope.launch {
            val rows = railAdapter.buildRows(
                viewModel.groups(),
                getString(R.string.browser_groups_all),
                getString(R.string.browser_no_group),
            )
            railAdapter.submit(rows)
        }
    }

    private fun onChannelClick(channel: ChannelEntity) {
        val listId = arguments?.getLong(ChannelBrowserFragment.ARG_LIST_ID, -1L) ?: -1L
        (activity as? TvNavigator.NavHost)?.openPlayer(listId, channel.position)
    }
}
