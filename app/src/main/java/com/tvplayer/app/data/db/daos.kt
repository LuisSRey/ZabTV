package com.tvplayer.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ListDao {
    @Query("SELECT * FROM lists ORDER BY id")
    suspend fun all(): List<ListEntity>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun get(id: Long): ListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: ListEntity): Long

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM lists")
    suspend fun clearAll()
}

@Dao
interface ChannelDao {
    /** Upsert idempotente por (listId, stableKey): la reimportación actualiza en sitio (spec F1). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(channels: List<ChannelEntity>)

    @Query("SELECT COUNT(*) FROM channels WHERE listId = :listId")
    suspend fun countForList(listId: Long): Int

    @Query("DELETE FROM channels WHERE listId = :listId")
    suspend fun deleteForList(listId: Long)

    @Query("DELETE FROM channels")
    suspend fun clearAll()

    @Query("SELECT groupName AS `group`, COUNT(*) AS count FROM channels WHERE listId = :listId GROUP BY groupName ORDER BY groupName")
    suspend fun groupsForList(listId: Long): List<GroupNameCount>

    @Query("SELECT * FROM channels WHERE listId = :listId AND (:group IS NULL OR groupName = :group) ORDER BY position")
    suspend fun allForListAndGroup(listId: Long, group: String?): List<ChannelEntity>

    @Query(
        "SELECT * FROM channels WHERE listId = :listId AND normName LIKE '%' || :normQuery || '%' ORDER BY normName LIMIT 100",
    )
    suspend fun searchByNorm(listId: Long, normQuery: String): List<ChannelEntity>

    /** Fuente de paginado para el browser (Task 7): int-paginación por posición. */
    @Query("SELECT * FROM channels WHERE listId = :listId AND (:group IS NULL OR groupName = :group) ORDER BY position")
    fun pagingForListAndGroup(listId: Long, group: String?): androidx.paging.PagingSource<Int, ChannelEntity>

    /** Canal en una posición exacta (zapping, Task 8). */
    @Query("SELECT * FROM channels WHERE listId = :listId AND position = :pos LIMIT 1")
    suspend fun channelAtPosition(listId: Long, pos: Int): ChannelEntity?

    /** Resolución por clave estable (último canal aunque el orden cambie). */
    @Query("SELECT * FROM channels WHERE listId = :listId AND stableKey = :stableKey LIMIT 1")
    suspend fun channelByKey(listId: Long, stableKey: String): ChannelEntity?

    /** Siguiente / previo en el orden de la lista (zapping arriba/abajo, Task 8). */
    @Query("SELECT * FROM channels WHERE listId = :listId AND position > :pos ORDER BY position ASC LIMIT 1")
    suspend fun nextInList(listId: Long, pos: Int): ChannelEntity?

    @Query("SELECT * FROM channels WHERE listId = :listId AND position < :pos ORDER BY position DESC LIMIT 1")
    suspend fun prevInList(listId: Long, pos: Int): ChannelEntity?
}

data class GroupNameCount(val group: String, val count: Int)

@Dao
interface FavouriteDao {
    /** Toggle; devuelve el estado final (true = marcado). PK compuesta: (listId, channelKey). */
    @Transaction
    suspend fun toggle(listId: Long, channelKey: String): Boolean {
        return if (existsBlocking(listId, channelKey)) {
            delete(listId, channelKey)
            false
        } else {
            insert(FavouriteEntity(listId, channelKey, System.currentTimeMillis()))
            true
        }
    }

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE listId = :listId AND channelKey = :channelKey)")
    suspend fun existsBlocking(listId: Long, channelKey: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE listId = :listId AND channelKey = :channelKey)")
    suspend fun exists(listId: Long, channelKey: String): Boolean

    @Insert
    suspend fun insert(favourite: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE listId = :listId AND channelKey = :channelKey")
    suspend fun delete(listId: Long, channelKey: String)

    @Query("DELETE FROM favourites")
    suspend fun clearAll()

    @Query(
        "SELECT c.* FROM channels c JOIN favourites f ON f.listId = c.listId AND f.channelKey = c.stableKey " +
            "WHERE c.listId = :listId ORDER BY f.addedAtMillis DESC",
    )
    suspend fun favouritesListId(listId: Long): List<ChannelEntity>
}

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(programs: List<EpgProgramEntity>)

    @Query("SELECT COUNT(*) FROM epg_programs")
    suspend fun count(): Int

    /** Solo el programa marcado `en el aire` por canal: estructuras mínima; el Pair se arma en service. */
    @Query("SELECT * FROM epg_programs WHERE tvgId IN (:tvgIds) AND startMillis <= :atMillis AND endMillis > :atMillis ORDER BY tvgId, startMillis")
    suspend fun nowRows(tvgIds: List<String>, atMillis: Long): List<EpgProgramEntity>

    @Query(
        "SELECT p.* FROM epg_programs p JOIN (SELECT tvgId, MIN(startMillis) AS ms FROM epg_programs " +
            "WHERE tvgId IN (:tvgIds) AND startMillis > :atMillis GROUP BY tvgId) f ON p.tvgId = f.tvgId AND p.startMillis = f.ms " +
            "ORDER BY p.tvgId",
    )
    suspend fun nextRows(tvgIds: List<String>, atMillis: Long): List<EpgProgramEntity>

    /** Implementación del contrato del plan: Map<tvgId, Pair<ahora, siguiente>>. */
    @Transaction
    suspend fun nowNext(tvgIds: List<String>, atMillis: Long): Map<String, Pair<EpgProgramEntity?, EpgProgramEntity?>> {
        val now = nowRows(tvgIds, atMillis).associateBy { it.tvgId }
        val next = nextRows(tvgIds, atMillis).associateBy { it.tvgId }
        val ids = now.keys + next.keys
        return ids.associateWith { now[it] to next[it] }
    }

    @Query("SELECT * FROM epg_programs WHERE tvgId = :tvgId AND startMillis >= :startInclMillis AND startMillis <= :endInclMillis ORDER BY startMillis")
    suspend fun dayPrograms(tvgId: String, startInclMillis: Long, endInclMillis: Long): List<EpgProgramEntity>

    @Query("DELETE FROM epg_programs WHERE endMillis < :beforeMillis")
    suspend fun pruneBefore(beforeMillis: Long): Int

    @Query("DELETE FROM epg_programs")
    suspend fun clearAll()

    /** Catálogo XMLTV para el fallback por nombre (Task 14). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<EpgChannelEntity>)

    @Query("SELECT * FROM epg_channels")
    suspend fun allEpgChannels(): List<EpgChannelEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM epg_programs WHERE tvgId = :tvgId LIMIT 1)")
    suspend fun hasPrograms(tvgId: String): Boolean
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun getOrNull(key: String): String?

    suspend fun get(key: String, default: String? = null): String? = getOrNull(key) ?: default

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    suspend fun put(key: String, value: String) = put(SettingEntity(key, value))

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)
}
