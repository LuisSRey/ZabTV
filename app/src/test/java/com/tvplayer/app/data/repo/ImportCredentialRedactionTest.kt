package com.tvplayer.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tvplayer.app.data.AppContainer
import com.tvplayer.app.data.db.AppDatabase
import com.tvplayer.app.data.db.ListEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T-F2 (security B-2): el rechazo de una URL con user-info NUNCA expone las
 * credenciales en el mensaje (el toast de Listas lo muestra verbatim vía
 * safeMessage). El mensaje sigue siendo accionable: dice que la URL trae
 * credenciales. No se relaja isAllowedHttpUrl (sigue vetando user-info).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportCredentialRedactionTest {

    private lateinit var db: AppDatabase
    private lateinit var service: ImportService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val container = AppContainer(context)
        container.setAllowHttp(true)
        service = ImportService(context, db, container.okHttp)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `rechazo con user info no expone credenciales y sigue accionable`() = runTest {
        val listId = db.listDao().upsert(ListEntity(name = "L", sourceUrl = "https://x/lista.m3u"))
        val result = service.import(
            listId,
            ImportSource.HttpSource("http://user:pass@host/lista.m3u", null),
        )
        assertTrue(result.rejected)
        val msg = result.message.orEmpty()
        assertFalse("mensaje filtra credenciales: $msg", msg.contains("user:pass@"))
        assertFalse("mensaje filtra password: $msg", msg.contains("pass@"))
        assertTrue("mensaje accionable (dice credenciales): $msg", msg.contains("credenciales"))
    }
}
