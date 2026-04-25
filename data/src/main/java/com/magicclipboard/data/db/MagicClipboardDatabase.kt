package com.magicclipboard.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ClipEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MagicClipboardDatabase : RoomDatabase() {
    abstract fun clipDao(): ClipDao
}

