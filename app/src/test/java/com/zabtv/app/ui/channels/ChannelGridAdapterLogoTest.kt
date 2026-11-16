package com.zabtv.app.ui.channels

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * U2 S4: bindLogo — tag-check (misma URL no recarga), placeholder+error,
 * crossfade apagado, size 96×56, esquinas 8dp, UrlPolicer antes de la red;
 * onViewRecycled limpia logo+tag. TDD RED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelGridAdapterLogoTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun logoHolder(): ChannelGridAdapter.Holder {
        val parent = RecyclerView(context)
        parent.layoutManager = LinearLayoutManager(context)
        return ChannelGridAdapter(onClick = {}).onCreateViewHolder(parent, 0)
    }

    @Test
    fun `misma URL no recarga (tag-check)`() {
        val adapter = ChannelGridAdapter(onClick = {})
        val logo = logoHolder().logo
        val url = "https://s/a.png"
        logo.tag = url
        val sentinel = ColorDrawable(0xFF00FF00.toInt())
        logo.setImageDrawable(sentinel)
        adapter.bindLogo(logo, url)
        assertSame(sentinel, logo.drawable)
        assertEquals(url, logo.tag)
    }

    @Test
    fun `URL distinta recarga y actualiza tag`() {
        val adapter = ChannelGridAdapter(onClick = {})
        val logo = logoHolder().logo
        logo.tag = "https://s/old.png"
        val next = "https://s/new.png"
        adapter.bindLogo(logo, next)
        assertEquals(next, logo.tag)
    }

    @Test
    fun `URL nula pinta placeholder y limpia tag`() {
        val adapter = ChannelGridAdapter(onClick = {})
        val logo = logoHolder().logo
        logo.tag = "https://s/old.png"
        adapter.bindLogo(logo, null)
        assertNull(logo.tag)
        assertNotNull(logo.drawable)
    }

    @Test
    fun `URL prohibida no toca red y pinta placeholder`() {
        val adapter = ChannelGridAdapter(onClick = {})
        val logo = logoHolder().logo
        adapter.bindLogo(logo, "acestream://abc123")
        assertNull(logo.tag)
        assertNotNull(logo.drawable)
    }

    @Test
    fun `onViewRecycled limpia logo y tag`() {
        val adapter = ChannelGridAdapter(onClick = {})
        val holder = logoHolder()
        holder.logo.tag = "https://s/a.png"
        holder.logo.setImageDrawable(ColorDrawable(0xFFFF0000.toInt()))
        adapter.onViewRecycled(holder)
        assertNull(holder.logo.tag)
        assertNull(holder.logo.drawable)
    }
}
