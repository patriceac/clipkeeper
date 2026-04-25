package com.magicclipboard.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.magicclipboard.data.model.ClipContentKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipDaoTest {
    private lateinit var database: MagicClipboardDatabase
    private lateinit var dao: ClipDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MagicClipboardDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.clipDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `cleanup only deletes expired clips without protection`() = runTest {
        dao.insert(clip(id = 1L, createdAt = 10L))
        dao.insert(clip(id = 2L, createdAt = 10L, isPinned = true))
        dao.insert(clip(id = 3L, createdAt = 10L, isFavorite = true))
        dao.insert(clip(id = 4L, createdAt = 10L, isPinned = true, isFavorite = true))
        dao.insert(clip(id = 5L, createdAt = 200L))

        dao.deleteExpired(100L)

        assertNull(dao.getById(1L))
        assertNotNull(dao.getById(2L))
        assertNotNull(dao.getById(3L))
        assertNotNull(dao.getById(4L))
        assertNotNull(dao.getById(5L))
    }

    @Test
    fun `unpinning refreshes the retention timestamp`() = runTest {
        dao.insert(clip(id = 1L, createdAt = 10L, isPinned = true))

        dao.togglePinned(id = 1L, updatedAt = 200L)
        dao.deleteExpired(100L)

        val actual = dao.getById(1L)
        assertNotNull(actual)
        assertFalse(actual!!.isPinned)
        assertEquals(200L, actual.createdAt)
    }

    @Test
    fun `unfavoriting refreshes the retention timestamp`() = runTest {
        dao.insert(clip(id = 1L, createdAt = 10L, isFavorite = true))

        dao.toggleFavorite(id = 1L, updatedAt = 200L)
        dao.deleteExpired(100L)

        val actual = dao.getById(1L)
        assertNotNull(actual)
        assertFalse(actual!!.isFavorite)
        assertEquals(200L, actual.createdAt)
    }

    @Test
    fun `protected clips sort ahead of fresh unprotected clips`() = runTest {
        dao.insert(clip(id = 1L, createdAt = 100L))
        dao.insert(clip(id = 2L, createdAt = 50L, isFavorite = true))
        dao.insert(clip(id = 3L, createdAt = 25L, isPinned = true))

        val ids = dao.observeAll().first().map { it.id }

        assertEquals(listOf(3L, 2L, 1L), ids)
    }

    private fun clip(
        id: Long,
        createdAt: Long,
        isPinned: Boolean = false,
        isFavorite: Boolean = false,
    ): ClipEntity = ClipEntity(
        id = id,
        kind = ClipContentKind.TEXT.name,
        encryptedText = "payload-$id",
        createdAt = createdAt,
        isPinned = isPinned,
        isFavorite = isFavorite,
    )
}
