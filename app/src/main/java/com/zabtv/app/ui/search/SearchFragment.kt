package com.zabtv.app.ui.search

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zabtv.app.R
import com.zabtv.app.TpApp
import com.zabtv.app.TvNavigator
import com.zabtv.app.ui.channels.ChannelGridAdapter
import com.zabtv.app.ui.common.FocusScaleHelper
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

        /**
         * SECCION-09 (D7/A1-01): Search = 1 columna con el ítem-fila del
         * browser en móvil Y TV (el browser usa spanCount 1 en todos los
         * formatos; el ítem es una fila de ancho completo). Costura pura de
         * test: siempre 1.
         */
        fun columns(isTv: Boolean): Int = 1

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
            (activity as? TvNavigator.NavHost)?.openTab(
                com.zabtv.app.ui.home.HomeFragment(),
                com.zabtv.app.ui.home.HomeFragment.TAG,
            )
            return
        }
        val container = (requireActivity().application as TpApp).container
        viewModel = SearchViewModel(container.db.channelDao(), listId)

        val isTv = FocusScaleHelper.isTv(view)
        val input = view.findViewById<EditText>(R.id.search_input)
        emptyView = view.findViewById(R.id.search_empty)
        val grid = view.findViewById<RecyclerView>(R.id.search_grid)
        gridAdapter = ChannelGridAdapter(onClick = { channel ->
            (activity as? TvNavigator.NavHost)?.openPlayer(channel.listId, channel.position)
        })
        grid.layoutManager = LinearLayoutManager(requireContext())
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
