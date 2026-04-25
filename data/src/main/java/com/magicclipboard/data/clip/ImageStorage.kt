package com.magicclipboard.data.clip

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class ImageStorage(
    private val filesDir: File,
) {
    private val clipDir = File(filesDir, "clip_images").apply { mkdirs() }

    suspend fun importEncrypted(
        sourceName: String,
        streamProvider: () -> InputStream?,
        encrypt: (ByteArray) -> ByteArray,
    ): String? = withContext(Dispatchers.IO) {
        val source = streamProvider() ?: return@withContext null
        source.use { input ->
            val encrypted = encrypt(input.readBytes())
            val target = File(clipDir, "${System.currentTimeMillis()}_${sourceName.take(24)}.mcbin")
            target.writeBytes(encrypted)
            target.absolutePath
        }
    }

    suspend fun decryptBytes(
        path: String,
        decrypt: (ByteArray) -> ByteArray,
    ): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null
        decrypt(file.readBytes())
    }

    suspend fun decodeBitmap(
        path: String,
        decrypt: (ByteArray) -> ByteArray,
    ): Bitmap? = withContext(Dispatchers.IO) {
        decryptBytes(path, decrypt)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        File(path).delete()
    }
}

