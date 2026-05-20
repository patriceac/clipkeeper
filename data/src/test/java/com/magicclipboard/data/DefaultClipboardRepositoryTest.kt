package com.magicclipboard.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.magicclipboard.data.clip.ImageStorage
import com.magicclipboard.data.db.ClipDao
import com.magicclipboard.data.db.ClipEntity
import com.magicclipboard.data.db.MagicClipboardDatabase
import com.magicclipboard.data.model.ClipContentKind
import com.magicclipboard.data.security.PayloadCipher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DefaultClipboardRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var database: MagicClipboardDatabase
    private lateinit var dao: ClipDao
    private lateinit var repository: DefaultClipboardRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MagicClipboardDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.clipDao()
        repository = DefaultClipboardRepository(
            context = ApplicationProvider.getApplicationContext(),
            clipDao = dao,
            payloadCipher = NoOpPayloadCipher,
            imageStorage = ImageStorage(temporaryFolder.newFolder("storage")),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `pruning expired clips deletes only unprotected expired image files`() = runTest {
        val expiredImage = imageFile("expired.mcbin")
        val pinnedImage = imageFile("pinned.mcbin")
        val freshImage = imageFile("fresh.mcbin")
        val now = System.currentTimeMillis()

        dao.insert(imageClip(id = 1L, createdAt = 10L, storagePath = expiredImage.absolutePath))
        dao.insert(imageClip(id = 2L, createdAt = 10L, storagePath = pinnedImage.absolutePath, isPinned = true))
        dao.insert(imageClip(id = 3L, createdAt = now, storagePath = freshImage.absolutePath))

        repository.pruneExpired(retentionHours = 1)

        assertNull(dao.getById(1L))
        assertFalse(expiredImage.exists())
        assertNotNull(dao.getById(2L))
        assertTrue(pinnedImage.exists())
        assertNotNull(dao.getById(3L))
        assertTrue(freshImage.exists())
    }

    @Test
    fun `update text replaces stored text for text clips`() = runTest {
        val saved = repository.saveText("Original text")

        val updated = repository.updateText(saved!!.id, "Edited text")

        assertNotNull(updated)
        assertEquals("Edited text", updated!!.text)
        assertEquals("Edited text", dao.getById(saved.id)?.encryptedText)
    }

    @Test
    fun `update text ignores image clips`() = runTest {
        dao.insert(imageClip(id = 12L, createdAt = System.currentTimeMillis(), storagePath = imageFile("image.mcbin").absolutePath))

        val updated = repository.updateText(12L, "Should not apply")

        assertNull(updated)
        assertNull(dao.getById(12L)?.encryptedText)
    }

    private fun imageFile(name: String): File =
        temporaryFolder.newFile(name).apply { writeBytes(byteArrayOf(1, 2, 3)) }

    private fun imageClip(
        id: Long,
        createdAt: Long,
        storagePath: String,
        isPinned: Boolean = false,
    ): ClipEntity = ClipEntity(
        id = id,
        kind = ClipContentKind.IMAGE.name,
        storagePath = storagePath,
        mimeType = "image/png",
        createdAt = createdAt,
        isPinned = isPinned,
    )
}

private object NoOpPayloadCipher : PayloadCipher {
    override fun encryptText(plainText: String): String = plainText

    override fun decryptText(payload: String): String = payload

    override fun encryptBytes(bytes: ByteArray): ByteArray = bytes

    override fun decryptBytes(bytes: ByteArray): ByteArray = bytes
}
