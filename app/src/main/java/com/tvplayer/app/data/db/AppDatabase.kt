package com.tvplayer.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migración 1→2 (Task 14): añade el catálogo `epg_channels` para el fallback
 * por nombre. Sin pérdida de datos (las demás tablas no se tocan).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `epg_channels` " +
                "(`tvgId` TEXT NOT NULL, `displayName` TEXT, PRIMARY KEY(`tvgId`))",
        )
    }
}

/**
 * Migración 2→3 (S4): override de reproductor externo por canal
 * (`channels.allowExternal`, null = Global). Columna anulable sin default:
 * las filas existentes quedan en Global sin tocar nada más.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `channels` ADD COLUMN `allowExternal` INTEGER")
    }
}

@Database(
    entities = [
        ListEntity::class,
        ChannelEntity::class,
        FavouriteEntity::class,
        EpgProgramEntity::class,
        EpgChannelEntity::class,
        SettingEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao
    abstract fun channelDao(): ChannelDao
    abstract fun favouriteDao(): FavouriteDao
    abstract fun epgDao(): EpgDao
    abstract fun settingsDao(): SettingsDao
}
