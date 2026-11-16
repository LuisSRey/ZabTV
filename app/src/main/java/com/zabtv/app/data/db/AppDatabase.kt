package com.zabtv.app.data.db

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

/**
 * Migración 3→4 (guía S1): historial por lista (`recent_watches`). Tabla nueva,
 * sin pérdida de datos (las demás tablas no se tocan).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recent_watches` " +
                "(`listId` INTEGER NOT NULL, `stableKey` TEXT NOT NULL, `watchedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`listId`, `stableKey`))",
        )
    }
}

@Database(
    entities = [
        ListEntity::class,
        ChannelEntity::class,
        FavouriteEntity::class,
        EpgProgramEntity::class,
        EpgChannelEntity::class,
        RecentWatchEntity::class,
        SettingEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao
    abstract fun channelDao(): ChannelDao
    abstract fun favouriteDao(): FavouriteDao
    abstract fun epgDao(): EpgDao
    abstract fun recentDao(): RecentDao
    abstract fun settingsDao(): SettingsDao
}
