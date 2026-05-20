package com.magicclipboard.data

import android.content.ClipData
import android.graphics.Bitmap
import com.magicclipboard.data.model.ClipEntry
import kotlinx.coroutines.flow.Flow
import java.io.File

interface ClipboardRepository {
    fun observeEntries(query: String = ""): Flow<List<ClipEntry>>

    suspend fun saveText(
        text: String,
        sourcePackage: String? = null,
    ): ClipEntry?

    suspend fun updateText(
        id: Long,
        text: String,
    ): ClipEntry?

    suspend fun saveExplicitClip(
        clipData: ClipData?,
        sourcePackage: String? = null,
    ): ClipEntry?

    suspend fun togglePinned(id: Long)

    suspend fun toggleFavorite(id: Long)

    suspend fun deleteEntry(id: Long)

    suspend fun clearAll()

    suspend fun pruneExpired(retentionHours: Int)

    suspend fun loadImagePreview(id: Long): Bitmap?

    suspend fun exportImage(id: Long, cacheDir: File): File?
}
