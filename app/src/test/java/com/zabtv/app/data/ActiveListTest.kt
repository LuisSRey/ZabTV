package com.zabtv.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zabtv.app.data.db.AppDatabase
import com.zabtv.app.data.db.ListEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lista activa (canales por defecto): la elige el usuario en Mis listas;
 * Home (Canales/Buscar/Favoritos) la usa con fallback a la primera.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActiveListTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `sin activar devuelve la primera lista`() = runTest {
        val a = db.listDao().upsert(ListEntity(name = "A", sourceUrl = "http://a/x.m3u"))
        db.listDao().upsert(ListEntity(name = "B", sourceUrl = "http://b/x.m3u"))
        assertEquals(a, ActiveList.resolve(db))
    }

    @Test
    fun `activar cambia la lista por defecto`() = runTest {
        db.listDao().upsert(ListEntity(name = "A", sourceUrl = "http://a/x.m3u"))
        val b = db.listDao().upsert(ListEntity(name = "B", sourceUrl = "http://b/x.m3u"))
        ActiveList.set(db, b)
        assertEquals(b, ActiveList.resolve(db))
    }

    @Test
    fun `si la activa se borra vuelve a la primera`() = runTest {
        val a = db.listDao().upsert(ListEntity(name = "A", sourceUrl = "http://a/x.m3u"))
        val b = db.listDao().upsert(ListEntity(name = "B", sourceUrl = "http://b/x.m3u"))
        ActiveList.set(db, b)
        db.listDao().delete(b)
        ActiveList.clearIf(db, b)
        assertEquals(a, ActiveList.resolve(db))
    }

    @Test
    fun `sin listas devuelve null`() = runTest {
        assertNull(ActiveList.resolve(db))
    }
}
