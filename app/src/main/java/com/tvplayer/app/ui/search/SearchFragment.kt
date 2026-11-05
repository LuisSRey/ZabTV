package com.tvplayer.app.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.TvNavigator
import com.tvplayer.app.ui.channels.ChannelGridAdapter
import com.tvplayer.app.ui.common.FocusScaleHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Búsqueda (Task 10): campo de texto (teclado IMF en TV vía inputType=text +
 * D-pad OK; táctil en móvil) + grilla de resultados con el adapter del browser.
 * Debounce de 300 ms para no golpear Room por tecla.
 */
class SearchFragment : Fragment() {

    companion object {
        const val TAG = "search"
        const val ARG_LIST_ID = "listId"

        fun newInstance(listId: Long) = SearchFragment().apply {
            arguments = bundleOf(ARG_LIST_ID to listId)
        }
    }

    private lateinit var viewModel: SearchViewModel
    private lateinit var gridAdapter: ChannelGridAdapter
    private lateinit var emptyView: TextView
    private var debounce: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val listId = arguments?.getLong(ARG_LIST_ID, -1L) ?: -1L
        if (listId < 0) {
            (activity as? TvNavigator.NavHost)?.open(
                com.tvplayer.app.ui.home.HomeFragment(),
                com.tvplayer.app.ui.home.HomeFragment.TAG,
            )
            return
        }
        val container = (requireActivity().application as TpApp).container
        viewModel = SearchViewModel(container.db.channelDao(), listId)

        val isTv = FocusScaleHelper.isTv(view)
        val input = view.findViewById<EditText>(R.id.search_input)
        emptyView = view.findViewById(R.id.search_empty)
        val grid = view.findViewById<RecyclerView>(R.id.search_grid)
        gridAdapter = ChannelGridAdapter { channel ->
            (activity as? TvNavigator.NavHost)?.openPlayer(channel.listId, channel.position)
        }
        grid.layoutManager = GridLayoutManager(requireContext(), if (isTv) 4 else 3)
        grid.adapter = gridAdapter
        emptyView.text = getString(R.string.search_hint)

        input.addTextChangedListener { editable ->
            debounce?.cancel()
            debounce = lifecycleScope.launch {
                delay(300)
                val hits = viewModel.search(editable?.toString().orEmpty())
                emptyView.visibility = if (hits.isEmpty()) View.VISIBLE else View.GONE
                if (hits.isNotEmpty()) emptyView.text = getString(R.string.search_empty)
                gridAdapter.submitData(PagingData.from(hits))
            }
        }
        // En TV el foco arranca en el campo para abrir el IME con OK del D-pad
        if (isTv) input.requestFocus()
    }

    override fun onDestroyView() {
        debounce?.cancel()
        super.onDestroyView()
    }
}
