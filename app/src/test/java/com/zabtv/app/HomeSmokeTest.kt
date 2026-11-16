package com.zabtv.app

import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeSmokeTest {

    @Test
    fun homeMuestraDisclaimerSinContenido() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val disclaimer = activity.findViewById<TextView>(R.id.home_disclaimer)
        assertNotNull(disclaimer)
        assertEquals(activity.getString(R.string.home_no_content), disclaimer.text.toString())
    }
}
