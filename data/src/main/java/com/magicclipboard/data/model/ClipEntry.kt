package com.magicclipboard.data.model

data class ClipEntry(
    val id: Long,
    val kind: ClipContentKind,
    val createdAt: Long,
    val text: String? = null,
    val mimeType: String? = null,
    val storagePath: String? = null,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val sourcePackage: String? = null,
) {
    val previewText: String
        get() = when (kind) {
            ClipContentKind.TEXT -> text.orEmpty()
            ClipContentKind.IMAGE -> "Image clip"
        }
}

