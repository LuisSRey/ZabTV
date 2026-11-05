package com.tvplayer.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.data.AppContainer
import com.tvplayer.app.data.db.AppDatabase
import com.tvplayer.app.data.db.EpgProgramEntity
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Task 13: ingesta EPG por streaming — solo la ventana [now-12h, now+60h] llega
 * a DB (insertBatch cada 2000), la segunda corrida poda lo antiguo (>5 días) y
 * el cap de 120 MB aborta limpio.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpgServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var service: EpgService

    /** now fijo: 2026-11-05T12:00:00Z. */
    private val now = 1_793_880_000_000L

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val container = AppContainer(context)
        container.setAllowHttp(true)
        service = EpgService(
            appContext = context,
            db = db,
            client = container.okHttp,
            nowProvider = { now },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun channelXml(id: String) =
        "<channel id=\"$id\"><display-name>$id</display-name></channel>"

    private fun programmeXml(tvgId: String, start: Long, end: Long, title: String): String {
        fun fmt(t: Long): String {
            val instant = java.time.Instant.ofEpochMilli(t)
            val dt = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
            return dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + " +0000"
        }
        return "<programme channel=\"$tvgId\" start=\"${fmt(start)}\" stop=\"${fmt(end)}\"><title>$title</title></programme>"
    }

    private fun tvXml(vararg bodies: String) = buildString {
        append("<tv>")
        append(channelXml("c1"))
        bodies.forEach { append(it) }
        append("</tv>")
    }

    @Test
    fun `solo la ventana llega a DB`() = runTest {
        val h = TimeUnit.HOURS.toMillis(1)
        val m = TimeUnit.MINUTES.toMillis(1)
        // 100 dentro de ventana [now-12h, now+60h]: de now-11h cada 40 min (span 66 h)
        val inside = (0 until 100).map {
            val s = now - 11 * h + it * 40 * m
            programmeXml("c1", s, s + 30 * m, "P$it")
        }
        val body = tvXml(
            *inside.toTypedArray(),
            // 50 fuera (pasado remoto y futuro lejano)
            *((0 until 25).map {
                programmeXml("c1", now - 3 * 24 * h - it * h, now - 3 * 24 * h - it * h + 30 * 60_000, "Viejo$it")
            }.toTypedArray()),
            *((0 until 25).map {
                programmeXml("c1", now + 8 * 24 * h + it * h, now + 8 * 24 * h + it * h + 30 * 60_000, "Fut$it")
            }.toTypedArray()),
        )
        server.enqueue(MockResponse().setBody(body))

        val result = service.refresh(server.url("/epg.xml").toString())

        assertEquals(100, result.inserted)
        // Los 50 fuera de ventana los descarta el parser antes de materializar
        // (spec F8): si colaran, el count sería 150.
        assertEquals(100, db.epgDao().count())
    }

    @Test
    fun `segunda corrida poda lo anterior a 5 días`() = runTest {
        val d = TimeUnit.DAYS.toMillis(1)
        // Programa anciano directo en DB
        db.epgDao().insertBatch(
            listOf(EpgProgramEntity(tvgId = "c1", startMillis = now - 6 * d, endMillis = now - 6 * d + 3_600_000, title = "Anciano")),
        )
        assertEquals(1, db.epgDao().count())

        val h = TimeUnit.HOURS.toMillis(1)
        server.enqueue(MockResponse().setBody(tvXml(programmeXml("c1", now + h, now + 2 * h, "Nuevo"))))
        val result = service.refresh(server.url("/epg.xml").toString())

        assertEquals(1, result.inserted)
        assertEquals(1, result.pruned)
        assertEquals(1, db.epgDao().count())
        assertEquals("Nuevo", db.epgDao().dayPrograms("c1", 0, Long.MAX_VALUE).first().title)
    }

    @Test
    fun `cap aborta limpio sin escribir nada`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)
        container.setAllowHttp(true)
        val smallCap = EpgService(
            appContext = context, db = db, client = container.okHttp,
            maxBodyBytes = 1000, nowProvider = { now },
        )
        val h = TimeUnit.HOURS.toMillis(1)
        val body = tvXml(
            *((0 until 40).map {
                programmeXml("c1", now + it * h, now + it * h + 30 * 60_000, "Programa número $it con título largo")
            }.toTypedArray()),
        )
        server.enqueue(MockResponse().setBody(body))

        val result = smallCap.refresh(server.url("/epg.xml").toString())

        assertTrue(result.rejected)
        assertTrue(result.message.orEmpty().contains("120 MB"))
        assertEquals(0, db.epgDao().count())
    }
}
