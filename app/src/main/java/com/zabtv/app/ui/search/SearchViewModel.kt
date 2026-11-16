package com.zabtv.app.ui.search

import androidx.lifecycle.ViewModel
import com.zabtv.app.core.Normalizer
import com.zabtv.app.data.db.ChannelDao
import com.zabtv.app.data.db.ChannelEntity

/**
 * Búsqueda (Task 10): la query se normaliza con la misma función que el import
 * (accent-insensible: "español" → "espanol") y el DAO busca por normName.
 * Query vacía → lista vacía sin tocar DB.
 */
class SearchViewModel(
    private val channelDao: ChannelDao,
    private val listId: Long,
) : ViewModel() {

    suspend fun search(rawQuery: String): List<ChannelEntity> {
        val query = Normalizer.normName(rawQuery)
        if (query.isBlank()) return emptyList()
        return channelDao.searchByNorm(listId, query)
    }
}
