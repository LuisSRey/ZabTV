package com.tvplayer.app.ui.player

import android.content.pm.PackageManager
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import com.tvplayer.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S3: el botón PiP existe solo en el layout móvil (el TV no lo trae) y queda
 * GONE cuando el dispositivo no ofrece FEATURE_PICTURE_IN_PICTURE.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerPipButtonTest {

    private fun activity() = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

    @Test
    fun `movil sin feature boton PiP GONE`() {
        val a = activity()
        // El shadow de Robolectric no ofrece PiP por defecto.
        assertEquals(
            false,
            a.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE),
        )
        val root = a.layoutInflater.inflate(R.layout.fragment_player, null)
        val btn = root.findViewById<ImageView>(R.id.player_pip)
        btn.visibility = PlayerFragment.pipButtonVisibility(
            PlayerFragment.pipAllowed(
                android.os.Build.VERSION.SDK_INT,
                a.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE),
                isTv = false,
            ),
        )
        assertEquals(View.GONE, btn.visibility)
    }

    @Test
    @Config(qualifiers = "television")
    fun `layout TV sin boton PiP`() {
        val a = activity()
        val root = a.layoutInflater.inflate(R.layout.fragment_player, null)
        assertNull(root.findViewById<View>(R.id.player_pip))
    }
}
