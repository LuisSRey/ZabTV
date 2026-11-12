package com.tvplayer.app.ui.channels

import com.tvplayer.app.core.xmltv.GuideWindow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * S3: la cabecera pinta la hora LOCAL del corte (el [GuideWindow] es epoch,
 * el formato va en la zona por defecto). Robolectric: [ChannelBrowserFragment]
 * no carga en JVM puro.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SlotsLabelsTzTest {

    private val defaultTz = TimeZone.getDefault()

    @Before
    fun resetTz() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTz() {
        TimeZone.setDefault(defaultTz)
    }

    private val anchor = GuideWindow.anchorFor(1_794_296_520_000L) // 07:30Z
    private val window = GuideWindow(anchor)

    private fun fmt(tzId: String, epoch: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone(tzId)
        }.format(java.util.Date(epoch))

    @Test
    fun `seis etiquetas con formato HH-mm`() {
        val labels = ChannelBrowserFragment.slotsLabels(window)
        assertEquals(6, labels.size)
        for (label in labels) {
            assertTrue(label.matches(Regex("\\d{2}:\\d{2}")))
        }
    }

    @Test
    fun `hora local en UTC`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val labels = ChannelBrowserFragment.slotsLabels(window)
        assertEquals(fmt("UTC", anchor), labels[0])
        assertEquals(fmt("UTC", anchor + 30L * 60_000L), labels[1])
    }

    @Test
    fun `misma ventana desplazada en GMT mas 2`() {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+02:00"))
        val labels = ChannelBrowserFragment.slotsLabels(window)
        assertEquals(fmt("GMT+02:00", anchor), labels[0])
        // El anchor (07:30Z) son las 09:30 en GMT+2.
        assertEquals("09:30", labels[0])
        // El desplazamiento es uniforme en los 6 cortes.
        for (i in 0..5) {
            assertEquals(fmt("GMT+02:00", window.slots()[i]), labels[i])
        }
    }
}
