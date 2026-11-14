package com.tvplayer.app.ui.channel_dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.tvplayer.app.R
import com.tvplayer.app.TpApp
import com.tvplayer.app.TvNavigator
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.repo.EpgMatcher
import com.tvplayer.app.data.repo.FavouritesRepo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ficha de canal (Task 14): logo, nombre, grupo, tira de programas del día
 * (consultas por rango) y acciones ver/favorito. Si la guía es fallback por
 * nombre se muestra la etiqueta "aproximado".
 */
class ChannelSheetFragment : DialogFragment() {

    companion object {
        const val TAG = "channel_sheet"
        private const val ARG_LIST_ID = "listId"
        private const val ARG_STABLE_KEY = "stableKey"

        fun newInstance(listId: Long, stableKey: String) = ChannelSheetFragment().apply {
            arguments = bundleOf(ARG_LIST_ID to listId, ARG_STABLE_KEY to stableKey)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_channel_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val listId = arguments?.getLong(ARG_LIST_ID, -1L) ?: -1L
        val stableKey = arguments?.getString(ARG_STABLE_KEY)
        val container = (requireActivity().application as TpApp).container
        val db = container.db
        val matcher = EpgMatcher(db.epgDao())

        val logo = view.findViewById<ImageView>(R.id.sheet_logo)
        val name = view.findViewById<TextView>(R.id.sheet_name)
        val group = view.findViewById<TextView>(R.id.sheet_group)
        val approx = view.findViewById<TextView>(R.id.sheet_approx)
        val programs = view.findViewById<RecyclerView>(R.id.sheet_programs)
        // SECCION-08 (D6/A1-03): findViewById de los 5 botones + attach de foco
        // FUERA de la corrutina (aplica aunque el canal no se encuentre). En TV
        // los botones ganan foco escalado; los listeners siguen dentro.
        val isTv = com.tvplayer.app.ui.common.FocusScaleHelper.isTv(view)
        val play = view.findViewById<Button>(R.id.sheet_play)
        val fav = view.findViewById<Button>(R.id.sheet_fav)
        val extGlobal = view.findViewById<Button>(R.id.sheet_ext_global)
        val extAlways = view.findViewById<Button>(R.id.sheet_ext_always)
        val extNever = view.findViewById<Button>(R.id.sheet_ext_never)
        if (isTv) {
            listOf(play, fav, extGlobal, extAlways, extNever).forEach {
                com.tvplayer.app.ui.common.FocusScaleHelper.attach(it)
            }
        }
        programs.layoutManager = LinearLayoutManager(requireContext())

        lifecycleScope.launch {
            val channel: ChannelEntity? = db.channelDao().allForListAndGroup(listId, null)
                .firstOrNull { it.stableKey == stableKey }
            if (channel == null) {
                dismiss()
                return@launch
            }
            name.text = channel.displayName
            group.text = channel.groupName.ifBlank { getString(R.string.browser_groups_all) }
            val rr = 10f * view.resources.displayMetrics.density
            channel.logoUrl?.let { logo.load(it) {
                transformations(coil.transform.RoundedCornersTransformation(rr))
            } }

            val match = matcher.match(channel)
            val now = System.currentTimeMillis()
            if (match == null) {
                approx.visibility = View.GONE
                programs.visibility = View.GONE
            } else {
                approx.visibility = if (match.approximate) View.VISIBLE else View.GONE
                val dayStart = startOfDay(now)
                val rows = matcher.dayPrograms(match.tvgId, dayStart, dayStart + 24 * 3_600_000)
                val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                val adapter = SheetProgramsAdapter(
                    rows.map { "${fmt.format(Date(it.startMillis))}  ${it.title}" },
                    bigText = isTv,
                )
                programs.adapter = adapter
            }

            play.setOnClickListener {
                dismiss()
                (activity as? TvNavigator.NavHost)?.openPlayer(channel.listId, channel.position)
            }
            // S4: override de reproductor externo (Global/Siempre/Nunca).
            fun paintExtRow(value: Boolean?) {
                extGlobal.alpha = if (value == null) 1f else 0.45f
                extAlways.alpha = if (value == true) 1f else 0.45f
                extNever.alpha = if (value == false) 1f else 0.45f
            }
            var extValue: Boolean? = channel.allowExternal
            paintExtRow(extValue)
            extGlobal.setOnClickListener {
                lifecycleScope.launch {
                    db.channelDao().setAllowExternal(channel.listId, channel.stableKey, null)
                    extValue = null
                    paintExtRow(null)
                }
            }
            extAlways.setOnClickListener {
                lifecycleScope.launch {
                    db.channelDao().setAllowExternal(channel.listId, channel.stableKey, true)
                    extValue = true
                    paintExtRow(true)
                }
            }
            extNever.setOnClickListener {
                lifecycleScope.launch {
                    db.channelDao().setAllowExternal(channel.listId, channel.stableKey, false)
                    extValue = false
                    paintExtRow(false)
                }
            }
            refreshFavLabel(fav, channel)
            fav.setOnClickListener {
                lifecycleScope.launch {
                    val added = FavouritesRepo(db).toggle(channel.listId, channel)
                    refreshFavLabel(fav, channel.copy())
                    Toast.makeText(
                        requireContext(),
                        if (added) R.string.fav_added else R.string.fav_removed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private suspend fun refreshFavLabel(fav: Button, channel: ChannelEntity) {
        val container = (requireActivity().application as TpApp).container
        val isFav = FavouritesRepo(container.db).isFavourite(channel.listId, channel)
        fav.text = getString(if (isFav) R.string.sheet_unfav else R.string.sheet_fav)
    }

    private fun startOfDay(now: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private class SheetProgramsAdapter(
        private val rows: List<String>,
        /** SECCION-08: fila de programas 16sp en TV (14sp móvil). */
        private val bigText: Boolean = false,
    ) :
        RecyclerView.Adapter<SheetProgramsAdapter.Holder>() {

        class Holder(root: View) : RecyclerView.ViewHolder(root) {
            val text: TextView = root as TextView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = TextView(parent.context).apply {
                setTextColor(parent.context.getColor(R.color.tp_text))
                textSize = if (bigText) 16f else 14f
                setPadding(0, 8, 0, 8)
            }
            return Holder(v)
        }

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.text.text = rows[position]
        }
    }
}
