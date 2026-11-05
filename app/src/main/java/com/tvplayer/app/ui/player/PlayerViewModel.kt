package com.tvplayer.app.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import com.tvplayer.app.data.db.ChannelDao
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.engine.Media3Engine
import com.tvplayer.app.engine.PlaybackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Lógica del player (Task 8): expone el estado del motor y zapping por posición
 * sobre el orden de la lista. No carga la lista entera en RAM.
 */
class PlayerViewModel(
    private val engine: Media3Engine,
    private val channelDao: ChannelDao,
    private val listId: Long,
    initial: ChannelEntity,
) : ViewModel() {

    private var current: ChannelEntity = initial

    fun play() = engine.play()
    fun pause() = engine.pause()
    fun release() = engine.release()

    fun zapUp() = CoroutineScope(Dispatchers.IO).launch {
        channelDao.nextInList(listId, current.position)?.let { target ->
            current = target
            engine.prepare(PlaybackSource.Progressive(target.url))
        }
    }

    fun zapDown() = CoroutineScope(Dispatchers.IO).launch {
        channelDao.prevInList(listId, current.position)?.let { target ->
            current = target
            engine.prepare(PlaybackSource.Progressive(target.url))
        }
    }
}
