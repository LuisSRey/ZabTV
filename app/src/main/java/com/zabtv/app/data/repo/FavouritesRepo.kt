package com.zabtv.app.data.repo

import com.zabtv.app.data.db.AppDatabase
import com.zabtv.app.data.db.ChannelEntity

/**
 * Favoritos (Task 9). La identidad del favorito es la StableKey del canal
 * (tvg-id o sha1 de norm+grupo — la misma que calcula ImportService), nunca la
 * URL ni el rowId: el favorito sobrevive a reimportaciones de la misma lista.
 */
class FavouritesRepo(private val db: AppDatabase) {

    /** Toggle; devuelve el estado final (true = marcado). */
    suspend fun toggle(listId: Long, channel: ChannelEntity): Boolean =
        db.favouriteDao().toggle(listId, channel.stableKey)

    suspend fun isFavourite(listId: Long, channel: ChannelEntity): Boolean =
        db.favouriteDao().exists(listId, channel.stableKey)

    /** Rail de favoritos en orden de marcado (más reciente primero). */
    suspend fun favourites(listId: Long): List<ChannelEntity> =
        db.favouriteDao().favouritesListId(listId)
}
