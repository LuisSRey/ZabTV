package com.zabtv.app.data

import com.zabtv.app.data.db.AppDatabase

/**
 * Lista activa = la de canales por defecto. La elige el usuario en Mis listas;
 * Home (Canales/Buscar/Favoritos) la usa con fallback a la primera lista.
 */
object ActiveList {

    const val KEY = "active_list_id"

    /** Id de la lista a usar, o null si no hay listas. */
    suspend fun resolve(db: AppDatabase): Long? {
        val lists = db.listDao().all()
        if (lists.isEmpty()) return null
        val active = db.settingsDao().get(KEY, null)?.toLongOrNull()
        return if (active != null && lists.any { it.id == active }) active else lists.first().id
    }

    suspend fun set(db: AppDatabase, listId: Long) {
        db.settingsDao().put(KEY, listId.toString())
    }

    /** Si se borra la lista activa, suelta la marca para que resuelva a la primera. */
    suspend fun clearIf(db: AppDatabase, listId: Long) {
        if (db.settingsDao().get(KEY, null)?.toLongOrNull() == listId) {
            db.settingsDao().delete(KEY)
        }
    }
}
