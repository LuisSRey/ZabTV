package com.tvplayer.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ListEntity::class,
        ChannelEntity::class,
        FavouriteEntity::class,
        EpgProgramEntity::class,
        SettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao
    abstract fun channelDao(): ChannelDao
    abstract fun favouriteDao(): FavouriteDao
    abstract fun epgDao(): EpgDao
    abstract fun settingsDao(): SettingsDao
}
