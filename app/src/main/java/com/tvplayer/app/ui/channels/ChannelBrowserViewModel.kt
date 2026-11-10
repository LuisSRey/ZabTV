package com.tvplayer.app.ui.channels

import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.tvplayer.app.core.xmltv.GuideWindow
import com.tvplayer.app.data.db.ChannelDao
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.EpgDao
import com.tvplayer.app.data.db.EpgProgramEntity
import com.tvplayer.app.data.db.GroupNameCount
import com.tvplayer.app.data.db.RecentDao
import com.tvplayer.app.data.repo.EpgMatcher
import com.tvplayer.app.ui.channels.ChannelSidebar.Selection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * Datos del browser de canales fusionado con la guía (S1). La grilla JAMÁS
 * carga la lista completa en RAM: PagingData lazy desde el DAO para
 * All/Group ([ChannelDao.pagingForListAndGroup]); Historial es un snapshot
 * [PagingData.from] con el cap 20 de [RecentDao]. Hero (destacado + ahora +
 * siguiente + progreso) y timeline de 3 h en cortes de 30 min ([GuideWindow]).
 * Room es el único estado: la UI observa DB, nunca cajas paralelas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelBrowserViewModel(
    listId: Long,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val recentDao: RecentDao,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /** Hero del browser: canal destacado + programa en el aire (si hay guía). */
    data class Hero(
        val channel: ChannelEntity,
        val nowTitle: String?,
        val approximate: Boolean = false,
        /** Siguiente programa (si hay guía). */
        val nextTitle: String? = null,
        /** Progreso del "ahora" 0..1 ([GuideWindow.progress]); 0 sin guía. */
        val progress: Float = 0f,
    )

    private val listSel = MutableStateFlow(listId)
    private val filterSel = MutableStateFlow<Selection>(Selection.All)

    /** Grilla paginada; cambia de fuente al seleccionar filtro o lista. */
    val channels: Flow<PagingData<ChannelEntity>> =
        combine(listSel, filterSel) { list, filter -> list to filter }
            .flatMapLatest { (list, filter) ->
                when (filter) {
                    is Selection.History -> flow { emit(PagingData.from(historyChannelsFor(list))) }
                    is Selection.All -> pager(list, null)
                    is Selection.Group -> pager(list, filter.name)
                }
            }

    private fun pager(list: Long, group: String?): Flow<PagingData<ChannelEntity>> = Pager(
        config = PagingConfig(pageSize = 60, prefetchDistance = 30, initialLoadSize = 120),
        pagingSourceFactory = { channelDao.pagingForListAndGroup(list, group) },
    ).flow

    /** La sidebar cambia la lista activa global: el flow re-emite solo. */
    fun setListId(id: Long) {
        listSel.value = id
    }

    fun select(filter: Selection) {
        filterSel.value = filter
    }

    /** Rail/sidebar de grupos con conteos (consulta puntual, determinista). */
    suspend fun groups(): List<GroupNameCount> = channelDao.groupsForList(listSel.value)

    /** Hero: último visto de la lista; sin historial, el primer canal. */
    suspend fun hero(): Hero? {
        val list = listSel.value
        val channels = channelDao.allForListAndGroup(list, null)
        if (channels.isEmpty()) return null
        val recentKey = recentDao.recentForList(list, 1).firstOrNull()?.stableKey
        val featured = recentKey?.let { channelDao.channelByKey(list, it) } ?: channels.first()
        val now = nowProvider()
        val match = EpgMatcher(epgDao).match(featured)
        val pair = match?.let { m ->
            epgDao.nowNext(listOf(m.tvgId), now)[m.tvgId]
        }
        val nowProgram = pair?.first
        return Hero(
            channel = featured,
            nowTitle = nowProgram?.title,
            approximate = match?.approximate ?: false,
            nextTitle = pair?.second?.title,
            progress = nowProgram?.let { GuideWindow.progress(now, it.startMillis, it.endMillis) } ?: 0f,
        )
    }

    /** Ventana del timeline: 3 h desde el corte de 30 min anterior a now. */
    fun window(now: Long = nowProvider()): GuideWindow = GuideWindow.now(now)

    /** Programas que solapan la ventana, repartidos por tvgId (orden de DB). */
    suspend fun timeline(tvgIds: List<String>, window: GuideWindow): Map<String, List<EpgProgramEntity>> {
        if (tvgIds.isEmpty()) return emptyMap()
        return epgDao.programsInRange(tvgIds, window.startMillis, window.endMillis)
            .groupBy { it.tvgId }
    }

    /** Canales de un grupo en orden de lista (`null` = todos). */
    suspend fun channelsForGroup(group: String?): List<ChannelEntity> =
        channelDao.allForListAndGroup(listSel.value, group)

    /** Historial de la lista en orden de visionado (ignora claves ya borradas). */
    suspend fun historyChannels(): List<ChannelEntity> = historyChannelsFor(listSel.value)

    private suspend fun historyChannelsFor(list: Long): List<ChannelEntity> {
        val keys = recentDao.recentForList(list).map { it.stableKey }
        if (keys.isEmpty()) return emptyList()
        return keys.mapNotNull { channelDao.channelByKey(list, it) }
    }

    /** Canales según el filtro del sidebar. */
    suspend fun channelsForFilter(filter: Selection): List<ChannelEntity> =
        when (filter) {
            is Selection.All -> channelsForGroup(null)
            is Selection.History -> historyChannels()
            is Selection.Group -> channelsForGroup(filter.name)
        }
}
