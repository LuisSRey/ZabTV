package com.tvplayer.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.tvplayer.app.R
import com.tvplayer.app.TvNavigator
import com.tvplayer.app.ui.common.FocusScaleHelper

/**
 * Home v0.1 (spec §5): sin listas muestra el disclaimer de "no hay contenido incluido"
 * + acceso directo a añadir lista. Las secciones reales llegan con datos (Tasks 7-11).
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
        FocusScaleHelper.attach(addTile)
        addTile.setOnClickListener {
            (activity as? TvNavigator.NavHost)?.open(AboutSpotDialog(), "about")
        }
        // Placeholder de navegación hasta Task 11: el tile apunta a un diálogo con el disclaimer
        // completo (§6.8) para que el flujo TV completo sea navegable desde el primer build.
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
