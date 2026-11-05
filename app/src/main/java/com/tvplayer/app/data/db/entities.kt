package com.tvplayer.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Esquema v0.1 del spec (§5). Reglas clave:
 * - `stable_key` = id-única del canal DENTRO de su lista (tvg-id o hash(nombre+grupo)),
 *   para que favoritos sobrevivan reimportaciones (spec F4).
 * - EPG vive acotado a la ventana temporal del spec (§F6); la poda es otro flujo.
 */

@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourceUrl: String = "",
    val epgUrl: String? = null,
    val userAgent: String? = null,
    val portalHost: String? = null,
    val portalUsernameEnc: String? = null,
    val portalPasswordEnc: String? = null,
    val lastImportedAt: Long = 0,
    val status: String = "new",
)

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["listId", "groupName"]),
        Index(value = ["tvgId"]),
        Index(value = ["normName"]),
        Index(value = ["listId", "stableKey"], unique = true),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val rowid: Long = 0,
    val listId: Long,
    val tvgId: String? = null,
    val displayName: String,
    val normName: String,
    val groupName: String = "",
    val logoUrl: String? = null,
    val url: String,
    val stableKey: String,
    val position: Int = 0,
)

@Entity(tableName = "favourites", primaryKeys = ["listId", "channelKey"])
data class FavouriteEntity(
    val listId: Long,
    val channelKey: String,
    val addedAtMillis: Long = 0,
)

@Entity(
    tableName = "epg_programs",
    indices = [Index(value = ["tvgId", "startMillis"], unique = true)],
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val rowid: Long = 0,
    val tvgId: String,
    val startMillis: Long,
    val endMillis: Long,
    val title: String,
    val description: String? = null,
)

/**
 * Catálogo de canales XMLTV (id + display-name) para el fallback por nombre
 * del matcher (Task 14). Se rellena en cada ingesta EPG.
 */
@Entity(tableName = "epg_channels")
data class EpgChannelEntity(
    @PrimaryKey val tvgId: String,
    val displayName: String? = null,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
