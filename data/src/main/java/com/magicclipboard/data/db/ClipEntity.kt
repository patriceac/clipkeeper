package com.magicclipboard.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clips")
data class ClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val kind: String,
    val encryptedText: String? = null,
    val storagePath: String? = null,
    val mimeType: String? = null,
    val createdAt: Long,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val sourcePackage: String? = null,
)

