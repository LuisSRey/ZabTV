package com.tvplayer.app.data

import com.tvplayer.app.data.db.AppDatabase
import com.tvplayer.app.data.db.ChannelEntity

/**
 * Último canal reproducido. Se guarda por stableKey (no por posición) para
 * sobrevivir reimports con orden distinto; se resuelve a la posición actual.
 */
object LastPlayed {

    private const val KEY_LIST = "last_list_id"
    private const val KEY_CHANNEL = "last_channel_key"

    suspend fun save(db: AppDatabase, listId: Long, stableKey: String) {
        db.settingsDao().put(KEY_LIST, listId.toString())
        db.settingsDao().put(KEY_CHANNEL, stableKey)
    }

    /** null si nunca se reprodujo nada o el canal ya no existe. */
    suspend fun resolve(db: AppDatabase): ChannelEntity? {
        val listId = db.settingsDao().get(KEY_LIST, null)?.toLongOrNull() ?: return null
        val key = db.settingsDao().get(KEY_CHANNEL, null) ?: return null
        return db.channelDao().channelByKey(listId, key)
    }
}
