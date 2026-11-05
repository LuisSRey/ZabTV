package com.tvplayer.app.ui.channels

import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.tvplayer.app.data.db.ChannelDao
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.GroupNameCount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Datos del browser de canales (Task 7). La grilla JAMÁS carga la lista completa
 * en RAM (listas de hasta ~30 MB): PagingData lazy desde el DAO. El grupo
 * seleccionado `null` = "Todos".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelBrowserViewModel(
    private val channelDao: ChannelDao,
    private val listId: Long,
) : ViewModel() {

    private val groupSel = MutableStateFlow<String?>(null)

    /** Grilla paginada; cambia de fuente al seleccionar grupo (flatMapLatest). */
    val channels: Flow<PagingData<ChannelEntity>> = groupSel
        .flatMapLatest { group ->
            Pager(
                config = PagingConfig(pageSize = 60, prefetchDistance = 30, initialLoadSize = 120),
                pagingSourceFactory = { channelDao.pagingForListAndGroup(listId, group) },
            ).flow
        }

    /** Rail de grupos con conteos (consulta puntual, determinista). */
    suspend fun groups(): List<GroupNameCount> = channelDao.groupsForList(listId)

    /** `null` vuelve a "Todos". */
    fun select(group: String?) {
        groupSel.value = group
    }
}
