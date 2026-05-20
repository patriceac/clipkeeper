package com.magicclipboard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {
    @Query("SELECT * FROM clips ORDER BY isPinned DESC, isFavorite DESC, createdAt DESC")
    fun observeAll(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips")
    suspend fun getAllNow(): List<ClipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ClipEntity): Long

    @Query(
        """
        UPDATE clips
        SET encryptedText = :encryptedText,
            createdAt = :updatedAt
        WHERE id = :id AND kind = :kind
        """,
    )
    suspend fun updateText(
        id: Long,
        kind: String,
        encryptedText: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE clips
        SET isPinned = NOT isPinned,
            createdAt = CASE WHEN isPinned = 1 THEN :updatedAt ELSE createdAt END
        WHERE id = :id
        """,
    )
    suspend fun togglePinned(id: Long, updatedAt: Long)

    @Query(
        """
        UPDATE clips
        SET isFavorite = NOT isFavorite,
            createdAt = CASE WHEN isFavorite = 1 THEN :updatedAt ELSE createdAt END
        WHERE id = :id
        """,
    )
    suspend fun toggleFavorite(id: Long, updatedAt: Long)

    @Query("DELETE FROM clips WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM clips")
    suspend fun clearAll()

    @Query("DELETE FROM clips WHERE isPinned = 0 AND isFavorite = 0 AND createdAt < :olderThan")
    suspend fun deleteExpired(olderThan: Long)

    @Transaction
    suspend fun deleteExpiredAndReturn(olderThan: Long): List<ClipEntity> {
        val expired = getExpired(olderThan)
        deleteExpired(olderThan)
        return expired
    }

    @Query("SELECT * FROM clips WHERE isPinned = 0 AND isFavorite = 0 AND createdAt < :olderThan")
    suspend fun getExpired(olderThan: Long): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ClipEntity?
}
