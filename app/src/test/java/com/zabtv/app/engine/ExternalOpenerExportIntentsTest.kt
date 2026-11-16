package com.zabtv.app.engine

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * SECCION-03 (T-P0-3 · D2/A3-02): contrato del intent de export coherente con
 * la detección. El probe de Ajustes usa el tipo genérico de vídeo; el export
 * abre con CANDIDATOS [MIME exacto, tipo genérico de vídeo] (dedup si el
 * exacto ya es el genérico), así cualquier app que aparece en el spinner
 * resuelve el intent: las que declaran solo-MIME-exacto y las que declaran
 * solo el genérico. El chooser usa el genérico SIEMPRE (mismo conjunto que
 * el spinner). Progressive (sin MIME) pasa al genérico (antes salía sin type
 * y solo lo resolvían apps solo-esquema, fuera del spinner).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExternalOpenerExportIntentsTest {

    private val hlsUrl = "https://cdn.example.com/canal.m3u8"
    private val hlsMime = "application/vnd.apple.mpegurl"
    private val mp4Url = "https://cdn.example.com/video.mp4"
    private val fakePkg = "com.fake.player"

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun registerIntent(intent: Intent, pkg: String) {
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.PlayActivity"
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, info)
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(intent).setPackage(pkg),
            info,
        )
    }

    @Test
    fun `a HLS produce candidatos exacto luego video`() {
        val candidates = ExternalOpener.exportCandidates(hlsUrl, hlsMime)
        assertEquals(2, candidates.size)
        assertEquals(hlsMime, candidates[0].type)
        assertEquals("video/*", candidates[1].type)
        assertEquals(hlsUrl, candidates[0].data.toString())
        assertEquals(hlsUrl, candidates[1].data.toString())
    }

    @Test
    fun `b DASH produce candidatos exacto luego video`() {
        val candidates = ExternalOpener.exportCandidates(
            "https://cdn.example.com/canal.mpd",
            "application/dash+xml",
        )
        assertEquals(2, candidates.size)
        assertEquals("application/dash+xml", candidates[0].type)
        assertEquals("video/*", candidates[1].type)
    }

    @Test
    fun `b2 Progressive mp4 produce solo video`() {
        val candidates = ExternalOpener.exportCandidates(mp4Url, null)
        assertEquals(1, candidates.size)
        assertEquals("video/*", candidates[0].type)
        assertEquals(mp4Url, candidates[0].data.toString())
    }

    @Test
    fun `b3 dedup cuando el exacto ya es video`() {
        val candidates = ExternalOpener.exportCandidates(mp4Url, "video/*")
        assertEquals(1, candidates.size)
        assertEquals("video/*", candidates[0].type)
    }

    @Test
    fun `c app solo video es usable y abre directo`() {
        registerIntent(ExternalOpener.buildIntent(hlsUrl, "video/*"), fakePkg)
        val candidates = ExternalOpener.exportCandidates(hlsUrl, hlsMime)
        assertTrue(
            ExternalOpener.isPreferredUsable(context.packageManager, fakePkg, candidates),
        )
        val activity = org.robolectric.Robolectric
            .buildActivity(com.zabtv.app.MainActivity::class.java).setup().get()
        assertTrue(ExternalOpener.openDirect(activity, candidates, fakePkg))
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(fakePkg, started.`package`)
        // El shadow PM solo filtra por ACTION (type/data-blind): el candidato
        // elegido no es observable aquí; en dispositivo real el matching es
        // estricto y el preferido solo-genérico abre por el candidato 2.
        assertEquals(hlsUrl, started.data.toString())
    }

    @Test
    fun `d app solo mime exacto es usable por candidato 1`() {
        registerIntent(ExternalOpener.buildIntent(hlsUrl, hlsMime), fakePkg)
        val candidates = ExternalOpener.exportCandidates(hlsUrl, hlsMime)
        assertTrue(
            ExternalOpener.isPreferredUsable(context.packageManager, fakePkg, candidates),
        )
    }

    @Test
    fun `d2 sin app que resuelve ningun candidato es no usable`() {
        val candidates = ExternalOpener.exportCandidates(hlsUrl, hlsMime)
        assertFalse(
            ExternalOpener.isPreferredUsable(context.packageManager, fakePkg, candidates),
        )
        assertFalse(ExternalOpener.canResolveAny(context.packageManager, candidates))
    }

    @Test
    fun `e chooserIntent es video y openWithChooser resuelve con apps solo video`() {
        registerIntent(ExternalOpener.buildIntent(hlsUrl, "video/*"), fakePkg)
        val chooser = ExternalOpener.chooserIntent(hlsUrl)
        assertEquals(hlsUrl, chooser.data.toString())
        assertEquals("video/*", chooser.type)
        val candidates = ExternalOpener.exportCandidates(hlsUrl, hlsMime)
        assertTrue(ExternalOpener.canResolveAny(context.packageManager, candidates))
        val activity = org.robolectric.Robolectric
            .buildActivity(com.zabtv.app.MainActivity::class.java).setup().get()
        assertTrue(ExternalOpener.openWithChooser(activity, candidates))
        assertEquals(Intent.ACTION_CHOOSER, shadowOf(activity).nextStartedActivity.action)
    }
}
