package com.magicclipboard.data

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.magicclipboard.data.clip.ImageStorage
import com.magicclipboard.data.db.ClipDao
import com.magicclipboard.data.db.ClipEntity
import com.magicclipboard.data.model.ClipContentKind
import com.magicclipboard.data.model.ClipEntry
import com.magicclipboard.data.security.PayloadCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class DefaultClipboardRepository(
    private val context: Context,
    private val clipDao: ClipDao,
    private val payloadCipher: PayloadCipher,
    private val imageStorage: ImageStorage,
) : ClipboardRepository {
    override fun observeEntries(query: String): Flow<List<ClipEntry>> {
        val normalizedQuery = query.trim().lowercase()
        return clipDao.observeAll().map { entities ->
            entities.mapNotNull(::toModel)
                .filter { entry ->
                    normalizedQuery.isBlank() || entry.previewText.lowercase().contains(normalizedQuery)
                }
        }
    }

    override suspend fun saveText(
        text: String,
        sourcePackage: String?,
    ): ClipEntry? = withContext(Dispatchers.IO) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return@withContext null
        persistText(normalizedText, sourcePackage)
    }

    override suspend fun updateText(
        id: Long,
        text: String,
    ): ClipEntry? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null

        val updatedRows = clipDao.updateText(
            id = id,
            kind = ClipContentKind.TEXT.name,
            encryptedText = payloadCipher.encryptText(text),
            updatedAt = System.currentTimeMillis(),
        )
        if (updatedRows == 0) return@withContext null

        clipDao.getById(id)?.let(::toModel)
    }

    override suspend fun saveExplicitClip(
        clipData: ClipData?,
        sourcePackage: String?,
    ): ClipEntry? = persistClipData(
        clipData = clipData,
        sourcePackage = sourcePackage,
    )

    override suspend fun togglePinned(id: Long) {
        clipDao.togglePinned(id, System.currentTimeMillis())
    }

    override suspend fun toggleFavorite(id: Long) {
        clipDao.toggleFavorite(id, System.currentTimeMillis())
    }

    override suspend fun deleteEntry(id: Long) {
        clipDao.getById(id)?.let { imageStorage.delete(it.storagePath) }
        clipDao.deleteById(id)
    }

    override suspend fun clearAll() {
        clipDao.getAllNow().forEach { entity ->
            imageStorage.delete(entity.storagePath)
        }
        clipDao.clearAll()
    }

    override suspend fun pruneExpired(retentionHours: Int) {
        val cutoff = System.currentTimeMillis() - (retentionHours * 60L * 60L * 1000L)
        clipDao.deleteExpiredAndReturn(cutoff).forEach { entity ->
            imageStorage.delete(entity.storagePath)
        }
    }

    override suspend fun loadImagePreview(id: Long): Bitmap? {
        val entity = clipDao.getById(id) ?: return null
        val path = entity.storagePath ?: return null
        return imageStorage.decodeBitmap(path, payloadCipher::decryptBytes)
    }

    override suspend fun exportImage(
        id: Long,
        cacheDir: File,
    ): File? = withContext(Dispatchers.IO) {
        val entity = clipDao.getById(id) ?: return@withContext null
        val path = entity.storagePath ?: return@withContext null
        val bytes = imageStorage.decryptBytes(path, payloadCipher::decryptBytes) ?: return@withContext null
        val mimeSubtype = entity.mimeType?.substringAfter('/') ?: "png"
        val file = File(cacheDir, "shared_${entity.id}.$mimeSubtype")
        file.writeBytes(bytes)
        file
    }

    private suspend fun persistClipData(
        clipData: ClipData?,
        sourcePackage: String?,
    ): ClipEntry? = withContext(Dispatchers.IO) {
        if (clipData == null) return@withContext null

        val item = clipData.getItemAt(0) ?: return@withContext null
        val uri = item.uri
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri).orEmpty()
            if (mimeType.startsWith("image/")) {
                return@withContext persistImage(uri, mimeType, sourcePackage)
            }
        }

        val text = item.coerceToText(context)?.toString()?.trim().orEmpty()
        if (text.isBlank()) return@withContext null

        persistText(text, sourcePackage)
    }

    private suspend fun persistText(
        text: String,
        sourcePackage: String?,
    ): ClipEntry? {
        val entity = ClipEntity(
            kind = ClipContentKind.TEXT.name,
            encryptedText = payloadCipher.encryptText(text),
            createdAt = System.currentTimeMillis(),
            sourcePackage = sourcePackage,
        )
        val id = clipDao.insert(entity)
        return toModel(entity.copy(id = id))
    }

    private suspend fun persistImage(
        uri: Uri,
        mimeType: String,
        sourcePackage: String?,
    ): ClipEntry? {
        val storagePath = imageStorage.importEncrypted(
            sourceName = guessFileName(context.contentResolver, uri),
            streamProvider = { context.contentResolver.openInputStream(uri) },
            encrypt = payloadCipher::encryptBytes,
        ) ?: return null
        val entity = ClipEntity(
            kind = ClipContentKind.IMAGE.name,
            storagePath = storagePath,
            mimeType = mimeType,
            createdAt = System.currentTimeMillis(),
            sourcePackage = sourcePackage,
        )
        val id = clipDao.insert(entity)
        return toModel(entity.copy(id = id))
    }

    private fun toModel(entity: ClipEntity): ClipEntry? {
        val kind = ClipContentKind.valueOf(entity.kind)
        val text = entity.encryptedText?.let(payloadCipher::decryptText)
        return ClipEntry(
            id = entity.id,
            kind = kind,
            createdAt = entity.createdAt,
            text = text,
            mimeType = entity.mimeType,
            storagePath = entity.storagePath,
            isPinned = entity.isPinned,
            isFavorite = entity.isFavorite,
            sourcePackage = entity.sourcePackage,
        )
    }

    private fun guessFileName(
        resolver: ContentResolver,
        uri: Uri,
    ): String {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "image"
    }
}
