package com.tvplayer.app.ui.guide

import androidx.lifecycle.ViewModel
import com.tvplayer.app.core.xmltv.GuideWindow
import com.tvplayer.app.data.db.ChannelDao
import com.tvplayer.app.data.db.ChannelEntity
import com.tvplayer.app.data.db.EpgDao
import com.tvplayer.app.data.db.EpgProgramEntity
import com.tvplayer.app.data.db.GroupNameCount
import com.tvplayer.app.ui.channels.ChannelSidebar
import com.tvplayer.app.data.db.RecentDao
import com.tvplayer.app.data.repo.EpgMatcher

/**
 * Datos de la guía de canales (S3): hero (último visto o primer canal, con su
 * "ahora" de EPG) + timeline de 3 h en cortes de 30 min ([GuideWindow]).
 * Room es el único estado: la UI observa DB, nunca cajas paralelas.
 */
class GuideViewModel(
    private val listId: Long,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val recentDao: RecentDao,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /** Hero de la guía: canal destacado + programa en el aire (si hay guía). */
    data class Hero(
        val channel: ChannelEntity,
        val nowTitle: String?,
        val approximate: Boolean = false,
        /** Siguiente programa (si hay guía). */
        val nextTitle: String? = null,
        /** Progreso del "ahora" 0..1 ([GuideWindow.progress]); 0 sin guía. */
        val progress: Float = 0f,
    )

    /** Destacado: último visto de la lista; sin historial, el primer canal. */
    suspend fun hero(): Hero? {
        val channels = channelDao.allForListAndGroup(listId, null)
        if (channels.isEmpty()) return null
        val recentKey = recentDao.recentForList(listId, 1).firstOrNull()?.stableKey
        val featured = recentKey?.let { channelDao.channelByKey(listId, it) } ?: channels.first()
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
        channelDao.allForListAndGroup(listId, group)

    /** Historial de la lista en orden de visionado (ignora claves ya borradas). */
    suspend fun historyChannels(): List<ChannelEntity> {
        val keys = recentDao.recentForList(listId).map { it.stableKey }
        if (keys.isEmpty()) return emptyList()
        return keys.mapNotNull { channelDao.channelByKey(listId, it) }
    }

    /** Canales según el filtro del sidebar. */
    suspend fun channelsForFilter(filter: ChannelSidebar.Selection): List<ChannelEntity> =
        when (filter) {
            is ChannelSidebar.Selection.All -> channelsForGroup(null)
            is ChannelSidebar.Selection.History -> historyChannels()
            is ChannelSidebar.Selection.Group -> channelsForGroup(filter.name)
        }

    /** Grupos con conteos para el sidebar. */
    suspend fun groups(): List<GroupNameCount> = channelDao.groupsForList(listId)
}
