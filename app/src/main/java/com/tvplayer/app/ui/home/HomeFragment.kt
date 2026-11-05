package com.tvplayer.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.TvNavigator
import com.tvplayer.app.data.repo.FavouritesRepo
import com.tvplayer.app.ui.channels.ChannelGridAdapter
import com.tvplayer.app.ui.common.FocusScaleHelper
import kotlinx.coroutines.launch

/**
 * Home v0.1 (spec §5): sin listas muestra el disclaimer de "no hay contenido incluido"
 * + acceso directo a añadir lista. Con listas, muestra el rail de favoritos
 * (Task 9, reutiliza el adapter del browser) sobre la primera lista.
 */
class HomeFragment : Fragment() {

    companion object {
        const val TAG = "home"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val addTile = view.findViewById<TextView>(R.id.home_add_list)
        val browseTile = view.findViewById<TextView>(R.id.home_browse)
        val searchTile = view.findViewById<TextView>(R.id.home_search)
        val settingsTile = view.findViewById<TextView>(R.id.home_settings)
        FocusScaleHelper.attach(addTile)
        FocusScaleHelper.attach(browseTile)
        FocusScaleHelper.attach(searchTile)
        FocusScaleHelper.attach(settingsTile)
        addTile.setOnClickListener {
            (activity as? TvNavigator.NavHost)?.openLists()
        }
        browseTile.setOnClickListener {
            val nav = activity as? TvNavigator.NavHost ?: return@setOnClickListener
            lifecycleScope.launch {
                val db = (requireActivity().application as TpApp).container.db
                val first = db.listDao().all().firstOrNull()
                if (first != null) nav.openBrowser(first.id) else nav.openLists()
            }
        }
        searchTile.setOnClickListener {
            val nav = activity as? TvNavigator.NavHost ?: return@setOnClickListener
            lifecycleScope.launch {
                val db = (requireActivity().application as TpApp).container.db
                val first = db.listDao().all().firstOrNull()
                if (first != null) {
                    nav.open(
                        com.tvplayer.app.ui.search.SearchFragment.newInstance(first.id),
                        com.tvplayer.app.ui.search.SearchFragment.TAG,
                    )
                }
            }
        }
        settingsTile.setOnClickListener {
            (activity as? TvNavigator.NavHost)?.openSettings()
        }

        val favsSection = view.findViewById<LinearLayout>(R.id.home_favs_section)
        val favsRail = view.findViewById<RecyclerView>(R.id.home_favs_rail)
        val favsAdapter = ChannelGridAdapter(onClick = { channel ->
            val listId = channel.listId
            (activity as? TvNavigator.NavHost)?.openPlayer(listId, channel.position)
        })
        favsRail.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        favsRail.adapter = favsAdapter

        lifecycleScope.launch {
            val db = (requireActivity().application as TpApp).container.db
            val firstList = db.listDao().all().firstOrNull()
            val favs = if (firstList != null) FavouritesRepo(db).favourites(firstList.id) else emptyList()
            if (favs.isNotEmpty()) {
                favsSection.visibility = View.VISIBLE
                favsAdapter.submitData(PagingData.from(favs))
            }
        }
    }
}

class AboutSpotDialog : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val text = TextView(requireContext())
        text.text = getString(com.tvplayer.app.R.string.about_disclaimer_full)
        text.setPadding(48, 48, 48, 48)
        text.setTextSize(16f)
        return text
    }
}
